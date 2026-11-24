package com.campus.contest.module.team.support.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.EditionRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.GrantRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.MemberRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.QuotaRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.RegistrationRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.TeamRow;
import com.campus.contest.module.team.support.model.RegistrationCheckMode;
import com.campus.contest.module.team.support.model.RegistrationEligibilityContext;
import com.campus.contest.module.team.support.model.RegistrationEligibilityResult;
import com.campus.contest.module.team.support.model.RegistrationMemberSnapshot;
import com.campus.contest.module.team.support.service.RegistrationEligibilityService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationEligibilityServiceImpl implements RegistrationEligibilityService {

    private static final Set<Integer> SUBMITTABLE_TEAM_STATUSES = Set.of(0, 4, 5);
    private static final int TEAM_PENDING_ADMIN = 2;
    private static final int EDITION_REGISTERING = 1;
    private static final int MAX_TEAM_SIZE = 5;

    private final RegistrationSupportMapper supportMapper;
    private final SysUserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationScopeService organizationScopeService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public RegistrationEligibilityResult checkCanRegister(
            RegistrationEligibilityContext context
    ) {
        validateContext(context);
        TeamRow team = supportMapper.selectTeam(context.teamId());
        if (team == null) {
            return rejected("NOT_VISIBLE", "队伍不存在或不可见");
        }
        if (!Objects.equals(team.editionId(), context.editionId())) {
            return rejected("REGISTRATION_STATE_INVALID", "队伍所属届次不匹配");
        }
        EditionRow edition = supportMapper.selectEdition(context.editionId());
        if (edition == null) {
            return rejected("NOT_VISIBLE", "比赛届次不存在或不可见");
        }

        SysUserDO leader = userMapper.selectById(team.leaderId());
        if (!activeUser(leader)) {
            return rejected("MEMBERS_INVALID", "队长账号不可用");
        }
        OrganizationDO leaderSchool = activeSchool(leader.getSchoolId());
        if (leaderSchool == null
                || !"VERIFIED".equalsIgnoreCase(leader.getSchoolVerificationStatus())) {
            return rejected("MEMBERS_INVALID", "队长学校归属尚未审核通过");
        }

        if (!isVisible(team, edition, leaderSchool)) {
            return rejected("NOT_VISIBLE", "比赛不在当前队伍可见范围内");
        }

        List<MemberRow> members = supportMapper.selectActiveMembers(team.id());
        String memberError = validateMembers(team, edition, leader, leaderSchool, members);
        if (memberError != null) {
            return rejected("MEMBERS_INVALID", memberError);
        }

        RegistrationRow registration = null;
        if (context.mode() == RegistrationCheckMode.SUBMIT) {
            if (!SUBMITTABLE_TEAM_STATUSES.contains(team.status())) {
                return rejected("REGISTRATION_STATE_INVALID", "当前队伍状态不允许提交报名");
            }
            if (edition.status() == null || edition.status() != EDITION_REGISTERING) {
                return rejected("DEADLINE_PASSED", "比赛当前未开放报名");
            }
            if (edition.registerDeadline() == null
                    || LocalDateTime.now(clock).isAfter(edition.registerDeadline())) {
                return rejected("DEADLINE_PASSED", "报名已截止");
            }
            if (supportMapper.countLiveRegistration(team.id(), edition.id()) > 0) {
                return rejected("ALREADY_REGISTERED", "当前队伍已有进行中或已通过的报名");
            }
        } else {
            if (edition.status() == null || edition.status() == 5) {
                return rejected("REGISTRATION_STATE_INVALID", "比赛届次已归档");
            }
            if (!Objects.equals(team.status(), TEAM_PENDING_ADMIN)
                    || context.registrationId() == null) {
                return rejected("REGISTRATION_STATE_INVALID", "当前队伍不在管理员终审阶段");
            }
            registration = supportMapper.selectRegistration(context.registrationId());
            if (registration == null
                    || !Objects.equals(registration.teamId(), team.id())
                    || !Objects.equals(registration.editionId(), edition.id())
                    || !"PENDING_ADMIN".equals(registration.status())) {
                return rejected("REGISTRATION_STATE_INVALID", "报名轮次状态不允许终审");
            }
            if (!snapshotMatches(registration.membersSnapshot(), members)) {
                return rejected("MEMBERS_INVALID", "当前成员与提交时的报名快照不一致");
            }
        }

        QuotaRow quota = supportMapper.selectQuota(edition.id(), leaderSchool.getId());
        if (context.mode() == RegistrationCheckMode.ADMIN_REVIEW
                && registration != null
                && Integer.valueOf(1).equals(registration.quotaHeld())
                && quota == null) {
            return rejected("SCHOOL_QUOTA_FULL", "报名轮次占用的学校名额记录已失效");
        }
        boolean limited = quota != null && quota.maxTeams() != null;
        boolean adminQuotaNotHeld = context.mode() == RegistrationCheckMode.ADMIN_REVIEW
                && limited
                && (registration == null
                || !Integer.valueOf(1).equals(registration.quotaHeld())
                || !Objects.equals(registration.quotaSchoolId(), leaderSchool.getId()));
        if (adminQuotaNotHeld) {
            return rejected("REGISTRATION_STATE_INVALID", "报名轮次未持有有效的学校名额");
        }
        if (context.mode() == RegistrationCheckMode.SUBMIT && limitedQuotaFull(quota)) {
            return new RegistrationEligibilityResult(
                    false,
                    "SCHOOL_QUOTA_FULL",
                    "队长学校报名名额已满",
                    null,
                    leaderSchool.getId(),
                    quota == null ? null : quota.id(),
                    quota == null ? null : quota.maxTeams(),
                    quota == null ? null : quota.usedTeams()
            );
        }

        GrantRow grant = null;
        String mode = edition.entryMode() == null
                ? ""
                : edition.entryMode().trim().toUpperCase(Locale.ROOT);
        if ("INVITED".equals(mode)) {
            grant = activeGrant(team, leader, leaderSchool);
            if (grant == null) {
                return rejected("NOT_INVITED", "当前队伍不在比赛邀请名单中");
            }
        } else if ("QUALIFIED".equals(mode)) {
            grant = activeGrant(team, leader, leaderSchool);
            if (grant == null) {
                return rejected("NOT_QUALIFIED", "当前队伍不在晋级名单中");
            }
        } else if (!"OPEN".equals(mode)) {
            return rejected("REGISTRATION_STATE_INVALID", "比赛资格模式配置无效");
        }

        return new RegistrationEligibilityResult(
                true,
                switch (mode) {
                    case "INVITED" -> "INVITED_ONLY";
                    case "QUALIFIED" -> "QUALIFIED_ONLY";
                    default -> "OPEN";
                },
                null,
                grant == null ? null : grant.id(),
                leaderSchool.getId(),
                quota == null ? null : quota.id(),
                quota == null ? null : quota.maxTeams(),
                quota == null ? null : quota.usedTeams()
        );
    }

    private void validateContext(RegistrationEligibilityContext context) {
        if (context == null
                || context.teamId() == null
                || context.editionId() == null
                || context.mode() == null) {
            throw new BizException(ErrorCode.INVALID_PARAM.getCode(), "报名资格上下文不完整");
        }
    }

    private boolean isVisible(TeamRow team, EditionRow edition, OrganizationDO leaderSchool) {
        if (edition.status() == null || edition.status() == 0) {
            return false;
        }
        String visibility = edition.visibility() == null
                ? ""
                : edition.visibility().trim().toUpperCase(Locale.ROOT);
        return switch (visibility) {
            case "PUBLIC" -> true;
            case "SCHOOL" -> Objects.equals(edition.schoolId(), leaderSchool.getId());
            case "PROVINCE" -> sameProvince(edition.provinceCode(), leaderSchool.getProvinceCode());
            case "UNLISTED" -> {
                EffectiveScope scope = organizationScopeService.loadRequired(team.leaderId());
                yield scope.eventIds().contains(edition.id());
            }
            default -> false;
        };
    }

    private String validateMembers(
            TeamRow team,
            EditionRow edition,
            SysUserDO leader,
            OrganizationDO leaderSchool,
            List<MemberRow> members
    ) {
        if (members == null || members.isEmpty()) {
            return "队伍没有有效成员";
        }
        int min = edition.minTeamSize() == null ? 1 : edition.minTeamSize();
        int max = Math.min(
                MAX_TEAM_SIZE,
                edition.maxTeamSize() == null ? MAX_TEAM_SIZE : edition.maxTeamSize()
        );
        if (members.size() < min || members.size() > max) {
            return "队伍人数不符合本届要求";
        }
        Set<String> studentKeys = new HashSet<>();
        Map<Long, OrganizationDO> schoolCache = new HashMap<>();
        boolean leaderPresent = false;
        for (MemberRow member : members) {
            if (!Objects.equals(member.editionId(), team.editionId())
                    || !StringUtils.hasText(member.memberName())
                    || !StringUtils.hasText(member.studentNo())
                    || member.schoolId() == null) {
                return "成员资料不完整";
            }
            String studentNo = member.studentNo().trim();
            if (studentNo.isEmpty()
                    || !studentKeys.add(member.schoolId() + ":" + studentNo)) {
                return "队伍中存在重复成员";
            }
            OrganizationDO school = schoolCache.computeIfAbsent(
                    member.schoolId(),
                    organizationMapper::selectById
            );
            if (school == null
                    || !Integer.valueOf(1).equals(school.getStatus())
                    || !"SCHOOL".equals(school.getOrgType())) {
                return "成员学校不存在或不可用";
            }
            if (Integer.valueOf(1).equals(member.isLeader())) {
                if (!Objects.equals(member.userId(), leader.getId())
                        || !Objects.equals(member.schoolId(), leaderSchool.getId())) {
                    return "队长成员资料与账号档案不一致";
                }
                leaderPresent = true;
            }
            if (!Integer.valueOf(1).equals(edition.crossSchoolTeamAllowed())
                    && !Objects.equals(member.schoolId(), leaderSchool.getId())) {
                return "本届比赛不允许跨校组队";
            }
        }
        return leaderPresent ? null : "队伍缺少队长成员记录";
    }

    private boolean snapshotMatches(String snapshotJson, List<MemberRow> members) {
        if (!StringUtils.hasText(snapshotJson)) {
            return false;
        }
        try {
            List<RegistrationMemberSnapshot> snapshot = objectMapper.readValue(
                    snapshotJson,
                    new TypeReference<>() {
                    }
            );
            List<RegistrationMemberSnapshot> expected = members.stream()
                    .sorted(Comparator.comparing(
                                    (MemberRow row) -> Integer.valueOf(1).equals(row.isLeader()))
                            .reversed()
                            .thenComparing(MemberRow::id))
                    .map(row -> new RegistrationMemberSnapshot(
                            row.id(),
                            row.userId(),
                            row.memberName(),
                            row.schoolId(),
                            row.studentNo(),
                            Integer.valueOf(1).equals(row.isLeader())
                    ))
                    .toList();
            return snapshot != null && snapshot.equals(expected);
        } catch (Exception ignored) {
            return false;
        }
    }

    private GrantRow activeGrant(
            TeamRow team,
            SysUserDO leader,
            OrganizationDO leaderSchool
    ) {
        return supportMapper.selectActiveGrant(
                team.editionId(),
                team.id(),
                leader.getId(),
                leaderSchool.getId(),
                leader.getStudentNo()
        );
    }

    private boolean limitedQuotaFull(QuotaRow quota) {
        return quota != null
                && quota.maxTeams() != null
                && quota.usedTeams() != null
                && quota.usedTeams() >= quota.maxTeams();
    }

    private boolean activeUser(SysUserDO user) {
        return user != null
                && !Integer.valueOf(1).equals(user.getDeleted())
                && Integer.valueOf(1).equals(user.getStatus());
    }

    private OrganizationDO activeSchool(Long schoolId) {
        OrganizationDO school = schoolId == null ? null : organizationMapper.selectById(schoolId);
        return school != null
                && Integer.valueOf(1).equals(school.getStatus())
                && !Integer.valueOf(1).equals(school.getDeleted())
                && "SCHOOL".equals(school.getOrgType())
                ? school
                : null;
    }

    private boolean sameProvince(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    private RegistrationEligibilityResult rejected(String state, String reason) {
        return new RegistrationEligibilityResult(
                false,
                state,
                reason,
                null,
                null,
                null,
                null,
                null
        );
    }
}
