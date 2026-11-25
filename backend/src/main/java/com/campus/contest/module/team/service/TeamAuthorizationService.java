package com.campus.contest.module.team.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.module.team.entity.TeamTeacherApplicationDO;
import com.campus.contest.module.team.mapper.TeamTeacherApplicationMapper;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TeamAuthorizationService {

    private final CurrentUserProvider currentUserProvider;
    private final ContestAuthorizationService contestAuthorizationService;
    private final ContestAccessMapper contestAccessMapper;
    private final ContestEditionMapper contestEditionMapper;
    private final SysUserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final TeamTeacherApplicationMapper teacherApplicationMapper;

    public Long currentUserId() {
        return currentUserProvider.requireUserId();
    }

    public TeamReadScope currentReadScope() {
        Long userId = currentUserId();
        EffectiveScope scope = contestAuthorizationService.currentScope();
        return new TeamReadScope(
                scope.global(),
                userId,
                scope.organizationIds(),
                scope.provinceCodes(),
                scope.eventIds()
        );
    }

    public boolean isGlobalOperator() {
        return currentReadScope().global();
    }

    public void requireGlobalOperator() {
        if (!isGlobalOperator()) {
            throw dataForbidden("当前操作仅允许平台全局管理员");
        }
    }

    public ContestEditionDO requireVisibleEdition(Long editionId) {
        var criteria = contestAuthorizationService.currentAccessCriteria();
        criteria.setEditionId(editionId);
        ContestEditionDO edition = contestAccessMapper.selectEditionById(criteria);
        if (edition == null) {
            throw new BizException(
                    ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "届次不存在或对当前用户不可见"
            );
        }
        return edition;
    }

    /**
     * 队长、管理员走常规届次可见性；待确认教师或已确认教师可通过有效申请关系读取届次。
     */
    /**
     * 列表入口：教师有效申请关系可以参与届次可见性判断。
     */
    public ContestEditionDO requireEditionForList(Long editionId) {
        Long userId = currentUserId();
        var criteria = contestAuthorizationService.currentAccessCriteria();
        criteria.setEditionId(editionId);
        ContestEditionDO visible = contestAccessMapper.selectEditionById(criteria);
        if (visible != null) {
            return visible;
        }
        if (teacherApplicationMapper.countEffectiveForEdition(userId, editionId) > 0) {
            ContestEditionDO edition = contestEditionMapper.selectById(editionId);
            if (edition != null) {
                return edition;
            }
        }
        throw new BizException(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "届次不存在或对当前用户不可见"
        );
    }
    public ContestEditionDO requireEditionForTeamRead(TeamDO team) {
        Long userId = currentUserId();
        if (Objects.equals(userId, team.getTeacherId())
                || hasPendingTeacherApplication(team, userId)) {
            ContestEditionDO edition = contestEditionMapper.selectById(team.getEditionId());
            if (edition != null) {
                return edition;
            }
        }
        return requireVisibleEdition(team.getEditionId());
    }
    public void requireReadable(TeamDO team, ContestEditionDO edition) {
        TeamReadScope scope = currentReadScope();
        Long userId = scope.userId();
        if (scope.global()
                || Objects.equals(userId, team.getLeaderId())
                || Objects.equals(userId, team.getTeacherId())
                || hasPendingTeacherApplication(team, userId)
                || scopeAllowsEdition(scope, team, edition)) {
            return;
        }
        throw dataForbidden("无权查看该队伍");
    }

    public ContestEditionDO requireEditionForAdminReview(TeamDO team) {
        ContestEditionDO edition = contestEditionMapper.selectById(team.getEditionId());
        if (edition == null) {
            throw new BizException(
                    ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "届次不存在"
            );
        }
        requireAdminReviewScope(team, edition);
        return edition;
    }
    public void requireAdminReviewScope(TeamDO team, ContestEditionDO edition) {
        TeamReadScope scope = currentReadScope();
        if (!scopeAllowsEdition(scope, team, edition)) {
            throw dataForbidden("当前管理员不在该队伍的数据范围内");
        }
    }
    public void requireLeader(TeamDO team) {
        if (!currentUserId().equals(team.getLeaderId())) {
            throw dataForbidden("只有队长可以操作该队伍");
        }
    }

    public boolean scopeAllowsEdition(
            TeamReadScope scope,
            TeamDO team,
            ContestEditionDO edition
    ) {
        if (scope.global() || scope.eventIds().contains(edition.getId())) {
            return true;
        }
        if (matchesOrganization(scope.organizationIds(), edition.getOwnerOrgId())
                || matchesOrganization(scope.organizationIds(), edition.getSchoolId())) {
            return true;
        }
        Long editionProvince = normalizeProvince(edition.getProvinceCode());
        if (editionProvince != null && scope.provinceCodes().contains(editionProvince)) {
            return true;
        }

        SysUserDO leader = userMapper.selectById(team.getLeaderId());
        if (leader == null) {
            return false;
        }
        if (matchesOrganization(scope.organizationIds(), leader.getSchoolId())) {
            return true;
        }
        OrganizationDO school = leader.getSchoolId() == null
                ? null
                : organizationMapper.selectById(leader.getSchoolId());
        Long schoolProvince = school == null ? null : normalizeProvince(school.getProvinceCode());
        return schoolProvince != null && scope.provinceCodes().contains(schoolProvince);
    }

    public boolean hasPendingTeacherApplication(TeamDO team, Long userId) {
        if (team.getCurrentRegistrationId() == null || userId == null) {
            return false;
        }
        return teacherApplicationMapper.countEffectiveForRegistration(
                team.getCurrentRegistrationId(),
                userId
        ) > 0;
    }

    private boolean matchesOrganization(java.util.Set<Long> scopeIds, Long targetId) {
        return targetId != null && scopeIds.contains(targetId);
    }

    private Long normalizeProvince(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(provinceCode.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }
}
