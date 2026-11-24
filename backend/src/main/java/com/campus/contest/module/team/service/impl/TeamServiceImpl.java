package com.campus.contest.module.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.team.dto.TeamCreateRequest;
import com.campus.contest.module.team.dto.TeamMemberInput;
import com.campus.contest.module.team.dto.TeamPageQuery;
import com.campus.contest.module.team.dto.TeamUpdateRequest;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.module.team.entity.TeamMemberDO;
import com.campus.contest.module.team.enums.TeamStatus;
import com.campus.contest.module.team.mapper.TeamMapper;
import com.campus.contest.module.team.mapper.TeamMemberMapper;
import com.campus.contest.module.team.service.TeamAuditService;
import com.campus.contest.module.team.service.TeamAuthorizationService;
import com.campus.contest.module.team.service.TeamService;
import com.campus.contest.module.team.vo.TeamCreateVO;
import com.campus.contest.module.team.vo.TeamDetailVO;
import com.campus.contest.module.team.vo.TeamMemberVO;
import com.campus.contest.module.team.vo.TeamPageItemVO;
import com.campus.contest.module.team.vo.TeamPageVO;
import com.campus.contest.module.team.vo.TeamVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final int MAX_MEMBERS = 5;
    private static final int MAX_NORMAL_MEMBERS = 4;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final TeamAuthorizationService authorizationService;
    private final TeamAuditService auditService;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public TeamPageVO list(Long editionId, TeamPageQuery query) {
        ContestEditionDO edition = authorizationService.requireVisibleEdition(editionId);
        int page = normalizePage(query == null ? null : query.getPage());
        int size = normalizeSize(query == null ? null : query.getSize());
        Long currentUserId = authorizationService.currentUserId();
        boolean global = authorizationService.isGlobalOperator();

        LambdaQueryWrapper<TeamDO> wrapper = Wrappers.lambdaQuery(TeamDO.class)
                .eq(TeamDO::getEditionId, edition.getId());

        if (!global) {
            wrapper.and(scope -> scope
                    .eq(TeamDO::getLeaderId, currentUserId)
                    .or()
                    .eq(TeamDO::getTeacherId, currentUserId));
        }
        if (query != null) {
            if (hasText(query.getStatus())) {
                TeamStatus status = parseStatus(query.getStatus());
                wrapper.eq(TeamDO::getStatus, status.databaseValue());
            }
            if (hasText(query.getTopicId())) {
                wrapper.eq(TeamDO::getTopicId, parseId(query.getTopicId(), "赛题 ID"));
            }
            if (hasText(query.getTeacherId())) {
                wrapper.eq(TeamDO::getTeacherId, parseId(query.getTeacherId(), "指导教师 ID"));
            }
            if (hasText(query.getKeyword())) {
                String keyword = query.getKeyword().trim();
                wrapper.and(value -> value
                        .like(TeamDO::getTeamName, keyword)
                        .or()
                        .like(TeamDO::getTeamCode, keyword));
            }
            applySort(wrapper, query.getSort());
        } else {
            wrapper.orderByDesc(TeamDO::getCreatedAt);
        }

        IPage<TeamDO> result = teamMapper.selectPage(new Page<>(page, size), wrapper);
        List<TeamPageItemVO> items = result.getRecords().stream()
                .map(team -> new TeamPageItemVO(
                        String.valueOf(team.getId()),
                        team.getTeamName(),
                        team.getTeamCode(),
                        TeamStatus.fromDatabaseValue(team.getStatus()).name(),
                        team.getMemberCount() == null ? 0 : team.getMemberCount()
                ))
                .toList();
        return new TeamPageVO(items, result.getTotal(), page, size, result.getTotal() > (long) page * size);
    }

    @Override
    public TeamCreateVO create(Long editionId, TeamCreateRequest request, String idempotencyKey) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireIdempotencyKey(idempotencyKey);
        String teamName = normalizeTeamName(request.getTeamName());
        Long operatorId = authorizationService.currentUserId();
        ContestEditionDO edition = authorizationService.requireVisibleEdition(editionId);
        requireCreatingStatus(edition);
        SysUserDO leader = requireVerifiedLeader(operatorId);
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("edition_id", String.valueOf(editionId));
        fingerprint.put("team_name", teamName);
        fingerprint.put("leader_id", String.valueOf(operatorId));

        return executeIdempotently(
                operatorId,
                "TEAM_CREATE",
                idempotencyKey,
                fingerprint,
                TeamCreateVO.class,
                () -> doCreateWithDuplicateHandling(editionId, teamName, operatorId, leader)
        );
    }

    @Override
    public TeamDetailVO detail(Long teamId) {
        TeamDO team = requireTeam(teamId);
        ContestEditionDO edition = authorizationService.requireVisibleEdition(team.getEditionId());
        authorizationService.requireReadable(team);
        List<TeamMemberDO> activeMembers = listActiveMembers(teamId);
        RegistrationView registration = registrationView(team, edition, activeMembers);
        List<String> memberNames = activeMembers.stream()
                .map(TeamMemberDO::getMemberName)
                .toList();
        return new TeamDetailVO(
                String.valueOf(team.getId()),
                String.valueOf(team.getEditionId()),
                team.getTeamName(),
                team.getTeamCode(),
                team.getTopicId() == null ? null : String.valueOf(team.getTopicId()),
                String.valueOf(team.getLeaderId()),
                team.getTeacherId() == null ? null : String.valueOf(team.getTeacherId()),
                TeamStatus.fromDatabaseValue(team.getStatus()).name(),
                team.getMemberCount() == null ? activeMembers.size() : team.getMemberCount(),
                memberNames,
                team.getVersion(),
                team.getCurrentRegistrationId() == null
                        ? null : String.valueOf(team.getCurrentRegistrationId()),
                null,
                registration.canRegister(),
                registration.state(),
                registration.reason()
        );
    }

    @Override
    public TeamVersionVO update(Long teamId, TeamUpdateRequest request, String idempotencyKey) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireVersion(request.getVersion());
        if (request.getTeamName() == null && request.getMembers() == null) {
            throw invalidParam("至少需要修改队名或成员");
        }
        if (idempotencyKey != null && idempotencyKey.length() > 64) {
            throw invalidParam("Idempotency-Key 长度不能超过 64");
        }
        String teamName = request.getTeamName() == null ? null : normalizeTeamName(request.getTeamName());
        Long operatorId = authorizationService.currentUserId();
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("team_id", String.valueOf(teamId));
        fingerprint.put("version", request.getVersion());
        fingerprint.put("team_name", teamName);
        fingerprint.put("members", request.getMembers());
        return executeOptionallyIdempotently(
                operatorId,
                "TEAM_UPDATE",
                idempotencyKey,
                fingerprint,
                TeamVersionVO.class,
                () -> doUpdateWithDuplicateHandling(teamId, teamName, request, operatorId)
        );
    }

    @Override
    public List<TeamMemberVO> members(Long teamId) {
        TeamDO team = requireTeam(teamId);
        authorizationService.requireVisibleEdition(team.getEditionId());
        authorizationService.requireReadable(team);
        return listActiveMembers(teamId).stream()
                .map(member -> TeamMemberVO.from(
                        member.getId(),
                        member.getUserId(),
                        member.getMemberName(),
                        member.getSchoolId(),
                        member.getStudentNo(),
                        Integer.valueOf(1).equals(member.getIsLeader()),
                        member.getJoinTime()
                ))
                .toList();
    }

    @Override
    public void removeMember(Long teamId, Long memberId, Integer version, String idempotencyKey) {
        requireVersion(version);
        if (memberId == null) {
            throw invalidParam("成员 ID 不能为空");
        }
        if (idempotencyKey != null && idempotencyKey.length() > 64) {
            throw invalidParam("Idempotency-Key 长度不能超过 64");
        }
        Long operatorId = authorizationService.currentUserId();
        Map<String, Object> fingerprint = Map.of(
                "team_id", String.valueOf(teamId),
                "member_id", String.valueOf(memberId),
                "version", version
        );
        executeOptionallyIdempotently(
                operatorId,
                "TEAM_MEMBER_REMOVE",
                idempotencyKey,
                fingerprint,
                Object.class,
                () -> {
                    doRemoveMember(teamId, memberId, version, operatorId);
                    return null;
                }
        );
    }

    private TeamCreateVO doCreateWithDuplicateHandling(
            Long editionId,
            String teamName,
            Long operatorId,
            SysUserDO leader
    ) {
        try {
            TeamCreateVO result = transactionTemplate.execute(status ->
                    doCreate(editionId, teamName, operatorId, leader)
            );
            if (result == null) {
                throw new IllegalStateException("创建队伍事务未返回结果");
            }
            return result;
        } catch (DuplicateKeyException exception) {
            throw duplicateRelation("队伍名称或成员学号在本届次中已存在");
        }
    }

    private TeamCreateVO doCreate(
            Long editionId,
            String teamName,
            Long operatorId,
            SysUserDO leader
    ) {
        if (teamNameExists(editionId, teamName, null)) {
            throw duplicateRelation("同一届次内队伍名称已存在");
        }
        String leaderStudentNo = normalizeStudentNo(leader.getStudentNo());
        if (studentAlreadyActive(editionId, leader.getSchoolId(), leaderStudentNo)) {
            throw duplicateRelation("该队长已在本届次加入其他队伍");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        TeamDO team = new TeamDO();
        team.setEditionId(editionId);
        team.setTeamName(teamName);
        team.setTeamCode(nextTeamCode());
        team.setLeaderId(operatorId);
        team.setStatus(TeamStatus.DRAFT.databaseValue());
        team.setMemberCount(1);
        team.setCreatedAt(now);
        team.setCreatedBy(operatorId);
        team.setUpdatedAt(now);
        team.setUpdatedBy(operatorId);
        team.setDeleted(0);
        team.setVersion(0);
        teamMapper.insert(team);

        TeamMemberDO leaderMember = new TeamMemberDO();
        leaderMember.setTeamId(team.getId());
        leaderMember.setEditionId(editionId);
        leaderMember.setUserId(operatorId);
        leaderMember.setMemberName(normalizeMemberName(leader.getRealName()));
        leaderMember.setSchoolId(leader.getSchoolId());
        leaderMember.setStudentNo(leaderStudentNo);
        leaderMember.setIsLeader(1);
        leaderMember.setJoinTime(now);
        leaderMember.setStatus(1);
        leaderMember.setCreatedAt(now);
        leaderMember.setCreatedBy(operatorId);
        leaderMember.setUpdatedAt(now);
        leaderMember.setUpdatedBy(operatorId);
        leaderMember.setDeleted(0);
        leaderMember.setVersion(0);
        memberMapper.insert(leaderMember);

        auditService.record(operatorId, "TEAM_CREATE", Map.of(
                "edition_id", String.valueOf(editionId),
                "team_id", String.valueOf(team.getId()),
                "team_code", team.getTeamCode(),
                "member_count", 1
        ));
        return new TeamCreateVO(
                String.valueOf(team.getId()),
                team.getTeamCode(),
                TeamStatus.DRAFT.name(),
                String.valueOf(leaderMember.getId())
        );
    }

    private TeamVersionVO doUpdateWithDuplicateHandling(
            Long teamId,
            String teamName,
            TeamUpdateRequest request,
            Long operatorId
    ) {
        try {
            TeamVersionVO result = transactionTemplate.execute(status ->
                    doUpdate(teamId, teamName, request, operatorId)
            );
            if (result == null) {
                throw new IllegalStateException("修改队伍事务未返回结果");
            }
            return result;
        } catch (DuplicateKeyException exception) {
            throw duplicateRelation("队伍名称或成员学号在本届次中已存在");
        }
    }

    private TeamVersionVO doUpdate(
            Long teamId,
            String teamName,
            TeamUpdateRequest request,
            Long operatorId
    ) {
        TeamDO team = requireLockedTeam(teamId);
        authorizationService.requireLeader(team);
        requireEditable(team);
        if (!Objects.equals(team.getVersion(), request.getVersion())) {
            throw versionConflict();
        }
        if (teamName != null && teamNameExists(team.getEditionId(), teamName, teamId)) {
            throw duplicateRelation("同一届次内队伍名称已存在");
        }

        int memberCount = team.getMemberCount() == null ? 1 : team.getMemberCount();
        if (request.getMembers() != null) {
            List<TeamMemberDO> activeMembers = listActiveMembers(teamId);
            TeamMemberDO leader = activeMembers.stream()
                    .filter(member -> Integer.valueOf(1).equals(member.getIsLeader()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(
                            ErrorCode.BUSINESS_CONFLICT.getCode(),
                            "队伍缺少队长成员记录"
                    ));
            memberCount = replaceNormalMembers(
                    team, leader, activeMembers, request.getMembers(), operatorId
            );
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        TeamDO update = new TeamDO();
        update.setId(teamId);
        update.setVersion(request.getVersion());
        update.setTeamName(teamName);
        update.setMemberCount(memberCount);
        update.setUpdatedAt(now);
        update.setUpdatedBy(operatorId);
        if (teamMapper.updateById(update) != 1) {
            throw versionConflict();
        }

        auditService.record(operatorId, "TEAM_UPDATE", Map.of(
                "team_id", String.valueOf(teamId),
                "member_count", memberCount,
                "team_name_changed", teamName != null
        ));
        return new TeamVersionVO(String.valueOf(teamId), request.getVersion() + 1);
    }

    private int replaceNormalMembers(
            TeamDO team,
            TeamMemberDO leader,
            List<TeamMemberDO> activeMembers,
            List<TeamMemberInput> inputs,
            Long operatorId
    ) {
        if (inputs.size() > MAX_NORMAL_MEMBERS) {
            throw invalidParam("普通队员最多 4 人，全队最多 5 人");
        }

        Map<Long, TeamMemberDO> existingById = new HashMap<>();
        for (TeamMemberDO member : activeMembers) {
            if (!Integer.valueOf(1).equals(member.getIsLeader())) {
                existingById.put(member.getId(), member);
            }
        }

        Set<Long> retainedIds = new HashSet<>();
        Set<String> memberKeys = new HashSet<>();
        String leaderKey = studentKey(leader.getSchoolId(), leader.getStudentNo());
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<NormalMemberValue> values = new ArrayList<>();

        for (TeamMemberInput input : inputs) {
            String memberName = normalizeMemberName(input.getMemberName());
            Long schoolId = parseId(input.getSchoolId(), "学校 ID");
            String studentNo = normalizeStudentNo(input.getStudentNo());
            String key = studentKey(schoolId, studentNo);
            if (!memberKeys.add(key)) {
                throw invalidParam("同一学校同一学号不能重复录入");
            }
            if (leaderKey.equals(key)) {
                throw invalidParam("普通队员不能与队长重复");
            }
            requireActiveSchool(schoolId);
            Long memberId = hasText(input.getMemberId())
                    ? parseId(input.getMemberId(), "成员 ID")
                    : null;
            TeamMemberDO existing = null;
            if (memberId != null) {
                if (!retainedIds.add(memberId)) {
                    throw invalidParam("同一成员不能重复提交");
                }
                existing = existingById.get(memberId);
                if (existing == null) {
                    throw invalidParam("成员不存在或不属于当前队伍");
                }
            }
            values.add(new NormalMemberValue(memberId, existing, memberName, schoolId, studentNo));
        }

        for (TeamMemberDO existing : existingById.values()) {
            if (!retainedIds.contains(existing.getId())) {
                if (memberMapper.markInactive(
                        existing.getId(), existing.getVersion(), operatorId, now
                ) != 1) {
                    throw versionConflict();
                }
            }
        }

        for (NormalMemberValue value : values) {
            if (value.existing() != null) {
                TeamMemberDO update = new TeamMemberDO();
                update.setId(value.existing().getId());
                update.setMemberName(value.memberName());
                update.setSchoolId(value.schoolId());
                update.setStudentNo(value.studentNo());
                update.setUpdatedAt(now);
                update.setUpdatedBy(operatorId);
                update.setVersion(value.existing().getVersion());
                if (memberMapper.updateById(update) != 1) {
                    throw versionConflict();
                }
            } else {
                TeamMemberDO insert = new TeamMemberDO();
                insert.setTeamId(team.getId());
                insert.setEditionId(team.getEditionId());
                insert.setUserId(null);
                insert.setMemberName(value.memberName());
                insert.setSchoolId(value.schoolId());
                insert.setStudentNo(value.studentNo());
                insert.setIsLeader(0);
                insert.setJoinTime(now);
                insert.setStatus(1);
                insert.setCreatedAt(now);
                insert.setCreatedBy(operatorId);
                insert.setUpdatedAt(now);
                insert.setUpdatedBy(operatorId);
                insert.setDeleted(0);
                insert.setVersion(0);
                memberMapper.insert(insert);
            }
        }

        return values.size() + 1;
    }

    private void doRemoveMember(Long teamId, Long memberId, Integer version, Long operatorId) {
        TeamDO team = requireLockedTeam(teamId);
        authorizationService.requireLeader(team);
        requireEditable(team);
        if (!Objects.equals(team.getVersion(), version)) {
            throw versionConflict();
        }
        TeamMemberDO member = memberMapper.selectById(memberId);
        if (member == null
                || !Objects.equals(teamId, member.getTeamId())
                || !Integer.valueOf(1).equals(member.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "成员不存在");
        }
        if (Integer.valueOf(1).equals(member.getIsLeader())) {
            throw invalidParam("队长不能被移出队伍");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (memberMapper.markInactive(
                memberId, member.getVersion(), operatorId, now
        ) != 1) {
            throw versionConflict();
        }

        int newCount = Math.max(1, (team.getMemberCount() == null ? 1 : team.getMemberCount()) - 1);
        TeamDO update = new TeamDO();
        update.setId(teamId);
        update.setMemberCount(newCount);
        update.setUpdatedAt(now);
        update.setUpdatedBy(operatorId);
        update.setVersion(version);
        if (teamMapper.updateById(update) != 1) {
            throw versionConflict();
        }
        auditService.record(operatorId, "TEAM_MEMBER_REMOVE", Map.of(
                "team_id", String.valueOf(teamId),
                "member_id", String.valueOf(memberId),
                "member_count", newCount
        ));
    }

    private SysUserDO requireVerifiedLeader(Long userId) {
        SysUserDO user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw invalidParam("当前账号不存在或已停用");
        }
        if (!"VERIFIED".equalsIgnoreCase(user.getSchoolVerificationStatus())
                || user.getSchoolId() == null) {
            throw new BizException(
                    ErrorCode.DATA_FORBIDDEN.getCode(),
                    "队长学校归属尚未审核通过，不能创建队伍"
            );
        }
        requireActiveSchool(user.getSchoolId());
        if (!hasText(user.getRealName()) || !hasText(user.getStudentNo())) {
            throw invalidParam("队长姓名和学号必须完整");
        }
        return user;
    }

    private void requireActiveSchool(Long schoolId) {
        OrganizationDO school = schoolId == null ? null : organizationMapper.selectById(schoolId);
        if (school == null
                || !Integer.valueOf(1).equals(school.getStatus())
                || !"SCHOOL".equalsIgnoreCase(school.getOrgType())) {
            throw invalidParam("学校不存在或不可用");
        }
    }

    private TeamDO requireTeam(Long teamId) {
        if (teamId == null) {
            throw invalidParam("队伍 ID 不能为空");
        }
        TeamDO team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "队伍不存在");
        }
        return team;
    }

    private TeamDO requireLockedTeam(Long teamId) {
        if (teamId == null) {
            throw invalidParam("队伍 ID 不能为空");
        }
        TeamDO team = teamMapper.selectActiveByIdForUpdate(teamId);
        if (team == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "队伍不存在");
        }
        return team;
    }

    private void requireCreatingStatus(ContestEditionDO edition) {
        if (!Integer.valueOf(EditionStatus.REGISTERING.getDatabaseValue()).equals(edition.getStatus())) {
            throw new BizException(
                    ErrorCode.INVALID_STATE_TRANSITION.getCode(),
                    "当前届次未开放报名"
            );
        }
    }

    private void requireEditable(TeamDO team) {
        TeamStatus status = TeamStatus.fromDatabaseValue(team.getStatus());
        if (!status.editable()) {
            throw new BizException(
                    ErrorCode.INVALID_STATE_TRANSITION.getCode(),
                    "当前队伍状态不允许修改成员或队名"
            );
        }
    }

    private List<TeamMemberDO> listActiveMembers(Long teamId) {
        return memberMapper.selectList(
                Wrappers.<TeamMemberDO>lambdaQuery()
                        .eq(TeamMemberDO::getTeamId, teamId)
                        .eq(TeamMemberDO::getStatus, 1)
                        .orderByDesc(TeamMemberDO::getIsLeader)
                        .orderByAsc(TeamMemberDO::getId)
        );
    }

    private boolean teamNameExists(Long editionId, String teamName, Long excludeTeamId) {
        LambdaQueryWrapper<TeamDO> wrapper = Wrappers.<TeamDO>lambdaQuery()
                .eq(TeamDO::getEditionId, editionId)
                .eq(TeamDO::getTeamName, teamName);
        if (excludeTeamId != null) {
            wrapper.ne(TeamDO::getId, excludeTeamId);
        }
        return teamMapper.selectCount(wrapper) > 0;
    }

    private boolean studentAlreadyActive(Long editionId, Long schoolId, String studentNo) {
        return memberMapper.selectCount(
                Wrappers.<TeamMemberDO>lambdaQuery()
                        .eq(TeamMemberDO::getEditionId, editionId)
                        .eq(TeamMemberDO::getSchoolId, schoolId)
                        .eq(TeamMemberDO::getStudentNo, studentNo)
                        .eq(TeamMemberDO::getStatus, 1)
        ) > 0;
    }

    private RegistrationView registrationView(
            TeamDO team,
            ContestEditionDO edition,
            List<TeamMemberDO> members
    ) {
        TeamStatus status = TeamStatus.fromDatabaseValue(team.getStatus());
        if (status == TeamStatus.PENDING_TEACHER
                || status == TeamStatus.PENDING_ADMIN
                || status == TeamStatus.APPROVED) {
            return new RegistrationView(false, "ALREADY_REGISTERED", "当前队伍已进入报名流程");
        }
        int minimum = edition.getMinTeamSize() == null ? 1 : edition.getMinTeamSize();
        int maximum = edition.getMaxTeamSize() == null
                ? MAX_MEMBERS : Math.min(edition.getMaxTeamSize(), MAX_MEMBERS);
        if (members.size() < minimum || members.size() > maximum) {
            return new RegistrationView(false, "MEMBERS_INVALID", "队伍人数不符合届次要求");
        }
        if (!Integer.valueOf(1).equals(edition.getCrossSchoolTeamAllowed())) {
            long schoolCount = members.stream()
                    .map(TeamMemberDO::getSchoolId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            if (schoolCount > 1) {
                return new RegistrationView(false, "MEMBERS_INVALID", "该届次不允许跨校组队");
            }
        }
        if (!Integer.valueOf(EditionStatus.REGISTERING.getDatabaseValue()).equals(edition.getStatus())
                || edition.getRegisterDeadline() == null
                || LocalDateTime.now(BUSINESS_ZONE).isAfter(edition.getRegisterDeadline())) {
            return new RegistrationView(false, "DEADLINE_PASSED", "当前不在报名开放时间内");
        }
        if ("INVITED".equalsIgnoreCase(edition.getEntryMode())) {
            return new RegistrationView(false, "NOT_INVITED", "该届次仅允许受邀队伍报名，资格校验尚未接入");
        }
        if ("QUALIFIED".equalsIgnoreCase(edition.getEntryMode())) {
            return new RegistrationView(false, "NOT_QUALIFIED", "该届次仅允许荣获资格的队伍报名，资格校验尚未接入");
        }
        return new RegistrationView(true, "OPEN", "当前队伍可以提交报名");
    }

    private void applySort(LambdaQueryWrapper<TeamDO> wrapper, String sort) {
        String value = hasText(sort) ? sort.trim() : "-created_at";
        boolean ascending = !value.startsWith("-");
        String field = ascending ? value : value.substring(1);
        switch (field) {
            case "created_at" -> {
                if (ascending) wrapper.orderByAsc(TeamDO::getCreatedAt);
                else wrapper.orderByDesc(TeamDO::getCreatedAt);
            }
            case "team_name" -> {
                if (ascending) wrapper.orderByAsc(TeamDO::getTeamName);
                else wrapper.orderByDesc(TeamDO::getTeamName);
            }
            case "status" -> {
                if (ascending) wrapper.orderByAsc(TeamDO::getStatus);
                else wrapper.orderByDesc(TeamDO::getStatus);
            }
            case "member_count" -> {
                if (ascending) wrapper.orderByAsc(TeamDO::getMemberCount);
                else wrapper.orderByDesc(TeamDO::getMemberCount);
            }
            default -> throw invalidParam("不支持的排序字段");
        }
    }

    private TeamStatus parseStatus(String value) {
        try {
            return TeamStatus.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private Long parseId(String value, String fieldName) {
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw invalidParam(fieldName + " 必须为正整数");
        }
    }

    private String normalizeTeamName(String value) {
        if (!hasText(value)) {
            throw invalidParam("队伍名称不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw invalidParam("队伍名称不能超过 64 个字符");
        }
        return normalized;
    }

    private String normalizeMemberName(String value) {
        if (!hasText(value)) {
            throw invalidParam("成员姓名不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 32) {
            throw invalidParam("成员姓名不能超过 32 个字符");
        }
        return normalized;
    }

    private String normalizeStudentNo(String value) {
        if (!hasText(value)) {
            throw invalidParam("学号不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 20) {
            throw invalidParam("学号不能超过 20 个字符");
        }
        return normalized;
    }

    private String studentKey(Long schoolId, String studentNo) {
        return schoolId + ":" + studentNo;
    }

    private String nextTeamCode() {
        return "T" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 14)
                .toUpperCase(Locale.ROOT);
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            throw invalidParam("page 必须大于等于 1");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size < 1 || size > 100) {
            throw invalidParam("size 必须在 1-100 之间");
        }
        return size;
    }

    private void requireVersion(Integer version) {
        if (version == null || version < 0) {
            throw invalidParam("version 必须为非负整数");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (!hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw invalidParam("Idempotency-Key 必须为 1-64 位非空字符串");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException duplicateRelation(String message) {
        return new BizException(ErrorCode.DUPLICATE_RELATION.getCode(), message);
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "数据已被他人修改，请刷新后重试"
        );
    }

    private <T> T executeIdempotently(
            Long operatorId,
            String operation,
            String idempotencyKey,
            Object fingerprint,
            Class<T> resultType,
            Supplier<T> action
    ) {
        return idempotencyService.execute(
                new IdempotencyScope(IdempotencyScope.PrincipalType.USER, String.valueOf(operatorId)),
                operation,
                idempotencyKey,
                fingerprint,
                IDEMPOTENCY_TTL,
                resultType,
                action
        );
    }

    private <T> T executeOptionallyIdempotently(
            Long operatorId,
            String operation,
            String idempotencyKey,
            Object fingerprint,
            Class<T> resultType,
            Supplier<T> action
    ) {
        if (idempotencyKey == null) {
            return action.get();
        }
        return executeIdempotently(
                operatorId, operation, idempotencyKey, fingerprint, resultType, action
        );
    }

    private record NormalMemberValue(
            Long memberId,
            TeamMemberDO existing,
            String memberName,
            Long schoolId,
            String studentNo
    ) {
    }

    private record RegistrationView(boolean canRegister, String state, String reason) {
    }
}

