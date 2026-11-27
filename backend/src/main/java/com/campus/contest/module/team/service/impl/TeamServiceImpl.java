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
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.team.dto.AvailableTeacherQuery;
import com.campus.contest.module.team.dto.TeamApprovalRequest;
import com.campus.contest.module.team.dto.TeamCreateRequest;
import com.campus.contest.module.team.dto.TeamMemberInput;
import com.campus.contest.module.team.dto.TeamPageQuery;
import com.campus.contest.module.team.dto.TeamRegistrationSubmitRequest;
import com.campus.contest.module.team.dto.TeamUpdateRequest;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.module.team.entity.TeamMemberDO;
import com.campus.contest.module.team.entity.TeamRegistrationDO;
import com.campus.contest.module.team.entity.TeamTeacherApplicationDO;
import com.campus.contest.module.team.enums.TeamStatus;
import com.campus.contest.module.team.mapper.AvailableTeacherMapper;
import com.campus.contest.module.team.mapper.TeamMapper;
import com.campus.contest.module.team.mapper.TeamMemberMapper;
import com.campus.contest.module.team.mapper.TeamRegistrationMapper;
import com.campus.contest.module.team.mapper.TeamTeacherApplicationMapper;
import com.campus.contest.module.team.service.TeamAuditService;
import com.campus.contest.module.team.service.TeamAuthorizationService;
import com.campus.contest.module.team.service.TeamReadScope;
import com.campus.contest.module.team.service.TeamService;
import com.campus.contest.module.team.support.model.RegistrationCheckMode;
import com.campus.contest.module.team.support.model.RegistrationEligibilityContext;
import com.campus.contest.module.team.support.model.RegistrationEligibilityResult;
import com.campus.contest.module.team.support.model.RegistrationMemberSnapshot;
import com.campus.contest.module.team.support.model.SchoolQuotaReservation;
import com.campus.contest.module.team.support.service.RegistrationEligibilityService;
import com.campus.contest.module.team.support.service.SchoolQuotaService;
import com.campus.contest.module.team.vo.AvailableTeacherPageVO;
import com.campus.contest.module.team.vo.AvailableTeacherVO;
import com.campus.contest.module.team.vo.TeamApprovalVO;
import com.campus.contest.module.team.vo.TeamCreateVO;
import com.campus.contest.module.team.vo.TeamDetailVO;
import com.campus.contest.module.team.vo.TeamMemberVO;
import com.campus.contest.module.team.vo.TeamPageItemVO;
import com.campus.contest.module.team.vo.TeamPageVO;
import com.campus.contest.module.team.vo.TeamRegistrationSubmitVO;
import com.campus.contest.module.team.vo.TeamRegistrationWithdrawVO;
import com.campus.contest.module.team.vo.TeamVersionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final TeamRegistrationMapper registrationMapper;
    private final TeamTeacherApplicationMapper teacherApplicationMapper;
    private final AvailableTeacherMapper availableTeacherMapper;
    private final SysUserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final TeamAuthorizationService authorizationService;
    private final RegistrationEligibilityService eligibilityService;
    private final SchoolQuotaService quotaService;
    private final AuthorizationSnapshotService authorizationSnapshotService;
    private final TeamAuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @Override
    public TeamPageVO list(Long editionId, TeamPageQuery query) {
        ContestEditionDO edition = authorizationService.requireEditionForList(editionId);
        int page = normalizePage(query == null ? null : query.getPage());
        int size = normalizeSize(query == null ? null : query.getSize());
        TeamReadScope readScope = authorizationService.currentReadScope();

        LambdaQueryWrapper<TeamDO> wrapper = Wrappers.lambdaQuery(TeamDO.class)
                .eq(TeamDO::getEditionId, edition.getId());

        if (!readScope.global()) {
            wrapper.and(scope -> {
                scope.eq(TeamDO::getLeaderId, readScope.userId())
                        .or()
                        .eq(TeamDO::getTeacherId, readScope.userId());
                scope.or().inSql(
                        TeamDO::getId,
                        pendingTeacherTeamSql(readScope.userId())
                );
                if (!readScope.eventIds().isEmpty()) {
                    scope.or().in(TeamDO::getEditionId, readScope.eventIds());
                }
                if (!readScope.organizationIds().isEmpty()) {
                    scope.or().inSql(
                            TeamDO::getEditionId,
                            organizationScopeEditionSql(readScope.organizationIds())
                    );
                }
                if (!readScope.provinceCodes().isEmpty()) {
                    scope.or().inSql(
                            TeamDO::getEditionId,
                            provinceScopeEditionSql(readScope.provinceCodes())
                    );
                }
            });
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
    public AvailableTeacherPageVO availableTeachers(
            Long editionId,
            AvailableTeacherQuery query
    ) {
        ContestEditionDO edition = authorizationService.requireVisibleEdition(editionId);
        requireRegistrationWindow(edition);
        Long operatorId = authorizationService.currentUserId();
        requireVerifiedLeader(operatorId);
        AuthorizationSnapshot snapshot = authorizationSnapshotService.loadRequired(operatorId);
        if (snapshot.roleCodes() == null || !snapshot.roleCodes().contains("STUDENT")) {
            throw new BizException(
                    ErrorCode.DATA_FORBIDDEN.getCode(),
                    "只有学生队长可以选择指导教师"
            );
        }

        int page = normalizePage(query == null ? null : query.getPage());
        int size = normalizeTeacherPageSize(query == null ? null : query.getSize());
        String keyword = normalizeKeyword(query == null ? null : query.getKeyword());
        long total = availableTeacherMapper.count(keyword);
        long offsetValue = (long) (page - 1) * size;
        if (offsetValue > Integer.MAX_VALUE) {
            throw invalidParam("page 超出支持范围");
        }
        int offset = (int) offsetValue;
        List<AvailableTeacherVO> items = availableTeacherMapper
                .selectPage(keyword, offset, size)
                .stream()
                .map(row -> new AvailableTeacherVO(
                        String.valueOf(row.id()),
                        row.realName(),
                        String.valueOf(row.schoolId()),
                        row.schoolName(),
                        row.college(),
                        row.title()
                ))
                .toList();
        return new AvailableTeacherPageVO(
                items,
                total,
                page,
                size,
                total > (long) page * size
        );
    }

    @Override
    public AvailableTeacherPageVO approvalTeachers(
            Long editionId,
            AvailableTeacherQuery query
    ) {
        authorizationService.requireEditionForApprovalFilter(editionId);
        int page = normalizePage(query == null ? null : query.getPage());
        int size = normalizeTeacherPageSize(query == null ? null : query.getSize());
        String keyword = normalizeKeyword(query == null ? null : query.getKeyword());
        long total = availableTeacherMapper.count(keyword);
        long offsetValue = (long) (page - 1) * size;
        if (offsetValue > Integer.MAX_VALUE) {
            throw invalidParam("page 超出支持范围");
        }
        List<AvailableTeacherVO> items = availableTeacherMapper
                .selectPage(keyword, (int) offsetValue, size)
                .stream()
                .map(row -> new AvailableTeacherVO(
                        String.valueOf(row.id()),
                        row.realName(),
                        String.valueOf(row.schoolId()),
                        row.schoolName(),
                        row.college(),
                        row.title()
                ))
                .toList();
        return new AvailableTeacherPageVO(items, total, page, size, total > (long) page * size);
    }

    @Override
    public TeamCreateVO create(Long editionId, TeamCreateRequest request, String idempotencyKey) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireIdempotencyKey(idempotencyKey);
        String teamName = normalizeTeamName(request.getTeamName());
        Long operatorId = authorizationService.currentUserId();
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
                () -> doCreateWithDuplicateHandling(editionId, teamName, operatorId)
        );
    }

    @Override
    public TeamDetailVO detail(Long teamId) {
        TeamDO team = requireTeam(teamId);
        ContestEditionDO edition = authorizationService.requireEditionForTeamRead(team);
        authorizationService.requireReadable(team, edition);
        List<TeamMemberDO> activeMembers = listActiveMembers(teamId);
        RegistrationView registration = registrationView(team);
        CurrentRegistrationView currentRegistration = currentRegistrationView(team);
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
                currentRegistration.version(),
                currentRegistration.status(),
                currentRegistration.rejectStage(),
                currentRegistration.rejectReason(),
                currentRegistration.teacherId(),
                currentRegistration.teacherName(),
                currentRegistration.teacherApplicationStatus(),
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
        ContestEditionDO edition = authorizationService.requireEditionForTeamRead(team);
        authorizationService.requireReadable(team, edition);
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
                () -> transactionTemplate.execute(status -> {
                    doRemoveMember(teamId, memberId, version, operatorId);
                    return null;
                })
        );
    }

    @Override
    public TeamRegistrationSubmitVO submitRegistration(
            Long teamId,
            TeamRegistrationSubmitRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireIdempotencyKey(idempotencyKey);
        requireVersion(request.getVersion());
        Long teacherId = parseId(request.getTeacherId(), "指导教师 ID");
        String remark = normalizeRemark(request.getRemark());
        Long operatorId = authorizationService.currentUserId();
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("team_id", String.valueOf(teamId));
        fingerprint.put("version", request.getVersion());
        fingerprint.put("teacher_id", String.valueOf(teacherId));
        fingerprint.put("remark", remark);
        return executeIdempotently(
                operatorId,
                "TEAM_REGISTRATION_SUBMIT",
                idempotencyKey,
                fingerprint,
                TeamRegistrationSubmitVO.class,
                () -> doSubmitWithDuplicateHandling(
                        teamId, request.getVersion(), teacherId, remark, operatorId
                )
        );
    }

    @Override
    public TeamRegistrationWithdrawVO withdrawRegistration(
            Long teamId,
            Integer version,
            String idempotencyKey
    ) {
        requireIdempotencyKey(idempotencyKey);
        requireVersion(version);
        Long operatorId = authorizationService.currentUserId();
        Map<String, Object> fingerprint = Map.of(
                "team_id", String.valueOf(teamId),
                "registration_version", version
        );
        return executeIdempotently(
                operatorId,
                "TEAM_REGISTRATION_WITHDRAW",
                idempotencyKey,
                fingerprint,
                TeamRegistrationWithdrawVO.class,
                () -> transactionTemplate.execute(status ->
                        doWithdraw(teamId, version, operatorId)
                )
        );
    }

    @Override
    public TeamApprovalVO approveRegistration(
            Long teamId,
            TeamApprovalRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireIdempotencyKey(idempotencyKey);
        requireVersion(request.getVersion());
        String stage = normalizeApprovalStage(request.getStage());
        String decision = normalizeApprovalDecision(request.getDecision());
        String reason = normalizeRejectReason(request.getReason(), decision);
        Long operatorId = authorizationService.currentUserId();
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("team_id", String.valueOf(teamId));
        fingerprint.put("stage", stage);
        fingerprint.put("decision", decision);
        fingerprint.put("reason", reason);
        fingerprint.put("version", request.getVersion());
        return executeIdempotently(
                operatorId,
                "TEAM_REGISTRATION_APPROVE",
                idempotencyKey,
                fingerprint,
                TeamApprovalVO.class,
                () -> transactionTemplate.execute(status ->
                        doApproveRegistration(
                                teamId,
                                stage,
                                decision,
                                reason,
                                request.getVersion(),
                                operatorId
                        )
                )
        );
    }
    private TeamApprovalVO doApproveRegistration(
            Long teamId,
            String stage,
            String decision,
            String reason,
            Integer registrationVersion,
            Long operatorId
    ) {
        TeamDO team = requireLockedTeam(teamId);
        if (team.getCurrentRegistrationId() == null) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    "当前队伍没有报名轮次"
            );
        }
        TeamRegistrationDO registration = registrationMapper.selectActiveByIdForUpdate(
                team.getCurrentRegistrationId()
        );
        if (registration == null
                || !Objects.equals(registration.getTeamId(), teamId)
                || !Objects.equals(registration.getVersion(), registrationVersion)) {
            throw versionConflict();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if ("TEACHER".equals(stage)) {
            return doTeacherApproval(
                    team, registration, decision, reason, now, operatorId
            );
        }
        return doAdminApproval(
                team, registration, decision, reason, now, operatorId
        );
    }

    private TeamApprovalVO doTeacherApproval(
            TeamDO team,
            TeamRegistrationDO registration,
            String decision,
            String reason,
            LocalDateTime now,
            Long operatorId
    ) {
        if (!Integer.valueOf(TeamStatus.PENDING_TEACHER.databaseValue()).equals(team.getStatus())
                || !"PENDING_TEACHER".equals(registration.getStatus())) {
            throw invalidState("当前队伍不在教师确认阶段");
        }
        TeamTeacherApplicationDO application = teacherApplicationMapper.selectOne(
                Wrappers.<TeamTeacherApplicationDO>lambdaQuery()
                        .eq(TeamTeacherApplicationDO::getRegistrationId, registration.getId())
                        .eq(TeamTeacherApplicationDO::getTeacherId, operatorId)
                        .eq(TeamTeacherApplicationDO::getStatus, "PENDING")
        );
        if (application == null) {
            throw dataForbidden("当前教师不是该报名轮次的待确认教师");
        }

        if ("APPROVE".equals(decision)) {
            ContestEditionDO edition = authorizationService.requireEditionForTeamRead(team);
            if (teamMapper.lockActiveUserForUpdate(operatorId) == null) {
                throw dataForbidden("指导教师账号不存在或已停用");
            }
            int maxTeams = edition.getMaxTeamsPerTeacher() == null
                    ? 3 : edition.getMaxTeamsPerTeacher();
            long activeLoad = registrationMapper.countActiveTeacherLoad(
                    edition.getId(), operatorId, registration.getId()
            );
            if (activeLoad >= maxTeams) {
                throw new BizException(
                        ErrorCode.BUSINESS_CONFLICT.getCode(),
                        "指导教师确认队伍数已达到上限"
                );
            }
            updateTeacherApplication(application, "ACCEPTED", null, now, operatorId);
            updateRegistrationStatus(
                    registration, "PENDING_ADMIN", null, null, now, operatorId
            );
            if (teamMapper.updateTeacherAndStatus(
                    team.getId(),
                    team.getVersion(),
                    operatorId,
                    operatorId,
                    now,
                    TeamStatus.PENDING_ADMIN.databaseValue()
            ) != 1) {
                throw versionConflict();
            }
            auditService.record(operatorId, "TEAM_TEACHER_APPROVE", Map.of(
                    "team_id", String.valueOf(team.getId()),
                    "registration_id", String.valueOf(registration.getId()),
                    "teacher_id", String.valueOf(operatorId)
            ));
            return approvalResult(team.getId(), registration, "PENDING_ADMIN");
        }

        rejectRegistration(
                team,
                registration,
                "TEACHER",
                reason,
                now,
                operatorId,
                application
        );
        return approvalResult(team.getId(), registration, "REJECTED");
    }
    private TeamApprovalVO doAdminApproval(
            TeamDO team,
            TeamRegistrationDO registration,
            String decision,
            String reason,
            LocalDateTime now,
            Long operatorId
    ) {
        if (!Integer.valueOf(TeamStatus.PENDING_ADMIN.databaseValue()).equals(team.getStatus())
                || !"PENDING_ADMIN".equals(registration.getStatus())) {
            throw invalidState("当前队伍不在管理员终审阶段");
        }
        ContestEditionDO edition = authorizationService.requireEditionForAdminReview(team);
        if ("APPROVE".equals(decision)) {
            RegistrationEligibilityResult eligibility = eligibilityService.checkCanRegister(
                    new RegistrationEligibilityContext(
                            team.getId(),
                            edition.getId(),
                            registration.getId(),
                            RegistrationCheckMode.ADMIN_REVIEW
                    )
            );
            if (!eligibility.canRegister()) {
                rejectRegistration(
                        team,
                        registration,
                        "ADMIN",
                        eligibility.reason() == null ? "终审资格校验失败" : eligibility.reason(),
                        now,
                        operatorId,
                        null
                );
                return approvalResult(team.getId(), registration, "REJECTED");
            }
            updateRegistrationStatus(
                    registration, "APPROVED", null, null, now, operatorId
            );
            if (teamMapper.updateStatus(
                    team.getId(),
                    team.getVersion(),
                    operatorId,
                    now,
                    TeamStatus.APPROVED.databaseValue()
            ) != 1) {
                throw versionConflict();
            }
            auditService.record(operatorId, "TEAM_ADMIN_APPROVE", Map.of(
                    "team_id", String.valueOf(team.getId()),
                    "registration_id", String.valueOf(registration.getId())
            ));
            return approvalResult(team.getId(), registration, "APPROVED");
        }

        rejectRegistration(
                team, registration, "ADMIN", reason, now, operatorId, null
        );
        return approvalResult(team.getId(), registration, "REJECTED");
    }

    private void rejectRegistration(
            TeamDO team,
            TeamRegistrationDO registration,
            String stage,
            String reason,
            LocalDateTime now,
            Long operatorId,
            TeamTeacherApplicationDO application
    ) {
        if (reason == null || reason.isBlank()) {
            throw invalidParam("驳回原因不能为空");
        }
        if (application != null) {
            updateTeacherApplication(application, "REJECTED", reason, now, operatorId);
        }
        updateRegistrationStatus(
                registration, "REJECTED", stage, reason, now, operatorId
        );
        quotaService.release(registration.getId());
        if (teamMapper.withdrawAndClearTeacher(
                team.getId(),
                team.getVersion(),
                operatorId,
                now,
                TeamStatus.REJECTED.databaseValue()
        ) != 1) {
            throw versionConflict();
        }
        auditService.record(operatorId, "TEAM_REGISTRATION_REJECT", Map.of(
                "team_id", String.valueOf(team.getId()),
                "registration_id", String.valueOf(registration.getId()),
                "stage", stage,
                "reason", reason
        ));
    }

    private void updateTeacherApplication(
            TeamTeacherApplicationDO application,
            String status,
            String reason,
            LocalDateTime now,
            Long operatorId
    ) {
        TeamTeacherApplicationDO update = new TeamTeacherApplicationDO();
        update.setId(application.getId());
        update.setStatus(status);
        update.setReason(reason);
        update.setHandledAt(now);
        update.setUpdatedAt(now);
        update.setUpdatedBy(operatorId);
        update.setVersion(application.getVersion());
        if (teacherApplicationMapper.updateById(update) != 1) {
            throw versionConflict();
        }
    }

    private void updateRegistrationStatus(
            TeamRegistrationDO registration,
            String status,
            String rejectStage,
            String rejectReason,
            LocalDateTime now,
            Long operatorId
    ) {
        TeamRegistrationDO update = new TeamRegistrationDO();
        update.setId(registration.getId());
        update.setStatus(status);
        update.setRejectStage(rejectStage);
        update.setRejectReason(rejectReason);
        update.setDecidedAt("APPROVED".equals(status) || "REJECTED".equals(status) ? now : null);
        update.setAdminReviewedBy("ADMIN".equals(rejectStage) || "APPROVED".equals(status)
                ? operatorId : null);
        update.setUpdatedAt(now);
        update.setUpdatedBy(operatorId);
        update.setVersion(registration.getVersion());
        if (registrationMapper.updateById(update) != 1) {
            throw versionConflict();
        }
    }

    private TeamApprovalVO approvalResult(
            Long teamId,
            TeamRegistrationDO registration,
            String status
    ) {
        TeamRegistrationDO current = registrationMapper.selectById(registration.getId());
        int actualVersion = current == null
                ? registration.getVersion() + 1
                : current.getVersion();
        return new TeamApprovalVO(
                String.valueOf(teamId),
                status,
                actualVersion,
                String.valueOf(registration.getId())
        );
    }

    private String normalizeApprovalStage(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TEACHER", "ADMIN").contains(normalized)) {
            throw invalidParam("stage 只能是 TEACHER 或 ADMIN");
        }
        return normalized;
    }

    private String normalizeApprovalDecision(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT").contains(normalized)) {
            throw invalidParam("decision 只能是 APPROVE 或 REJECT");
        }
        return normalized;
    }

    private String normalizeRejectReason(String reason, String decision) {
        String normalized = reason == null ? null : reason.trim();
        if ("REJECT".equals(decision) && (normalized == null || normalized.isEmpty())) {
            throw invalidParam("驳回原因不能为空");
        }
        if (normalized != null && normalized.length() > 500) {
            throw invalidParam("驳回原因不能超过 500 个字符");
        }
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private BizException invalidState(String message) {
        return new BizException(ErrorCode.INVALID_STATE_TRANSITION.getCode(), message);
    }

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }
    private TeamRegistrationSubmitVO doSubmitWithDuplicateHandling(
            Long teamId,
            Integer teamVersion,
            Long teacherId,
            String remark,
            Long operatorId
    ) {
        try {
            TeamRegistrationSubmitVO result = transactionTemplate.execute(status ->
                    doSubmit(teamId, teamVersion, teacherId, remark, operatorId)
            );
            if (result == null) {
                throw new IllegalStateException("提交报名事务未返回结果");
            }
            return result;
        } catch (DuplicateKeyException exception) {
            throw new BizException(
                    ErrorCode.DUPLICATE_RELATION.getCode(),
                    "报名轮次重复"
            );
        }
    }

    private TeamRegistrationSubmitVO doSubmit(
            Long teamId,
            Integer teamVersion,
            Long teacherId,
            String remark,
            Long operatorId
    ) {
        TeamDO team = requireLockedTeam(teamId);
        authorizationService.requireLeader(team);
        requireEditable(team);
        if (!Objects.equals(team.getVersion(), teamVersion)) {
            throw versionConflict();
        }
        ContestEditionDO edition = authorizationService.requireVisibleEdition(team.getEditionId());
        RegistrationEligibilityResult eligibility = eligibilityService.checkCanRegister(
                new RegistrationEligibilityContext(
                        teamId,
                        edition.getId(),
                        null,
                        RegistrationCheckMode.SUBMIT
                )
        );
        if (!eligibility.canRegister()) {
            throw registrationConflict(eligibility);
        }
        SysUserDO teacher = requireAvailableTeacher(teacherId);
        List<TeamMemberDO> members = listActiveMembers(teamId);

        LocalDateTime now = LocalDateTime.now(clock);
        TeamRegistrationDO registration = new TeamRegistrationDO();
        registration.setTeamId(teamId);
        registration.setEditionId(edition.getId());
        registration.setRoundNo(registrationMapper.selectMaxRoundNo(teamId) + 1);
        registration.setStatus("PENDING_TEACHER");
        registration.setRemark(remark);
        registration.setMembersSnapshot(serialize(memberSnapshots(members), "成员快照序列化失败"));
        registration.setEligibilitySnapshot(serialize(
                eligibilitySnapshot(eligibility, now),
                "资格快照序列化失败"
        ));
        registration.setQuotaHeld(0);
        registration.setSubmittedAt(now);
        registration.setCreatedAt(now);
        registration.setCreatedBy(operatorId);
        registration.setUpdatedAt(now);
        registration.setUpdatedBy(operatorId);
        registration.setDeleted(0);
        registration.setVersion(0);
        registrationMapper.insert(registration);

        Long quotaSchoolId = eligibility.quotaSchoolId();
        if (quotaSchoolId == null) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    "报名资格结果缺少名额学校"
            );
        }
        SchoolQuotaReservation reservation = quotaService.occupy(
                registration.getId(),
                edition.getId(),
                quotaSchoolId
        );
        if (reservation.schoolId() != null && !Objects.equals(reservation.schoolId(), quotaSchoolId)) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    "学校名额占用学校不匹配"
            );
        }

        TeamTeacherApplicationDO application = new TeamTeacherApplicationDO();
        application.setRegistrationId(registration.getId());
        application.setTeacherId(teacher.getId());
        application.setStatus("PENDING");
        application.setAppliedAt(now);
        application.setCreatedAt(now);
        application.setCreatedBy(operatorId);
        application.setUpdatedAt(now);
        application.setUpdatedBy(operatorId);
        application.setDeleted(0);
        application.setVersion(0);
        teacherApplicationMapper.insert(application);

        TeamDO update = new TeamDO();
        update.setId(teamId);
        update.setStatus(TeamStatus.PENDING_TEACHER.databaseValue());
        update.setTeacherId(null);
        update.setCurrentRegistrationId(registration.getId());
        update.setUpdatedAt(now);
        update.setUpdatedBy(operatorId);
        update.setVersion(teamVersion);
        if (teamMapper.updateById(update) != 1) {
            throw versionConflict();
        }

        auditService.record(operatorId, "TEAM_REGISTRATION_SUBMIT", Map.of(
                "team_id", String.valueOf(teamId),
                "registration_id", String.valueOf(registration.getId()),
                "round_no", registration.getRoundNo(),
                "teacher_id", String.valueOf(teacherId)
        ));
        return new TeamRegistrationSubmitVO(
                String.valueOf(teamId),
                TeamStatus.PENDING_TEACHER.name(),
                teamVersion + 1,
                String.valueOf(registration.getId()),
                registration.getRoundNo()
        );
    }

    private TeamRegistrationWithdrawVO doWithdraw(
            Long teamId,
            Integer registrationVersion,
            Long operatorId
    ) {
        TeamDO team = requireLockedTeam(teamId);
        authorizationService.requireLeader(team);
        TeamStatus teamStatus = TeamStatus.fromDatabaseValue(team.getStatus());
        if (teamStatus != TeamStatus.PENDING_TEACHER
                && teamStatus != TeamStatus.PENDING_ADMIN) {
            throw new BizException(
                    ErrorCode.INVALID_STATE_TRANSITION.getCode(),
                    "当前队伍状态不允许撤回报名"
            );
        }
        if (team.getCurrentRegistrationId() == null) {
            throw new BizException(ErrorCode.BUSINESS_CONFLICT.getCode(), "当前队伍没有报名轮次");
        }
        TeamRegistrationDO registration = registrationMapper.selectActiveByIdForUpdate(
                team.getCurrentRegistrationId()
        );
        if (registration == null
                || !Objects.equals(registration.getTeamId(), teamId)
                || !Objects.equals(registration.getVersion(), registrationVersion)) {
            throw versionConflict();
        }
        if (!"PENDING_TEACHER".equals(registration.getStatus())
                && !"PENDING_ADMIN".equals(registration.getStatus())) {
            throw new BizException(
                    ErrorCode.INVALID_STATE_TRANSITION.getCode(),
                    "当前报名轮次不允许撤回"
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        quotaService.release(registration.getId());
        TeamRegistrationDO releasedRegistration = registrationMapper.selectById(registration.getId());
        if (releasedRegistration == null) {
            throw new BizException(ErrorCode.BUSINESS_CONFLICT.getCode(), "报名轮次不存在");
        }
        if (teacherApplicationMapper.withdrawCurrent(
                registration.getId(),
                operatorId,
                now,
                "队长撤回报名"
        ) == 0) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    "教师申请状态异常，无法撤回"
            );
        }

        int currentRegistrationVersion = releasedRegistration.getVersion();
        TeamRegistrationDO registrationUpdate = new TeamRegistrationDO();
        registrationUpdate.setId(registration.getId());
        registrationUpdate.setStatus("WITHDRAWN");
        registrationUpdate.setWithdrawnAt(now);
        registrationUpdate.setUpdatedAt(now);
        registrationUpdate.setUpdatedBy(operatorId);
        registrationUpdate.setVersion(currentRegistrationVersion);
        if (registrationMapper.updateById(registrationUpdate) != 1) {
            throw versionConflict();
        }

        if (teamMapper.withdrawAndClearTeacher(
                teamId,
                team.getVersion(),
                operatorId,
                now,
                TeamStatus.WITHDRAWN.databaseValue()
        ) != 1) {
            throw versionConflict();
        }

        auditService.record(operatorId, "TEAM_REGISTRATION_WITHDRAW", Map.of(
                "team_id", String.valueOf(teamId),
                "registration_id", String.valueOf(registration.getId()),
                "registration_version", registrationVersion
        ));
        return new TeamRegistrationWithdrawVO(
                String.valueOf(teamId),
                String.valueOf(registration.getId()),
                "WITHDRAWN",
                currentRegistrationVersion + 1
        );
    }
    private TeamCreateVO doCreateWithDuplicateHandling(
            Long editionId,
            String teamName,
            Long operatorId
    ) {
        try {
            TeamCreateVO result = transactionTemplate.execute(status -> {
                ContestEditionDO edition = authorizationService.requireVisibleEdition(editionId);
                requireRegistrationWindow(edition);
                SysUserDO leader = requireVerifiedLeader(operatorId);
                return doCreate(editionId, teamName, operatorId, leader);
            });
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
        requireRegistrationWindow(authorizationService.requireVisibleEdition(team.getEditionId()));
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
        requireRegistrationWindow(authorizationService.requireVisibleEdition(team.getEditionId()));
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

    private void requireRegistrationWindow(ContestEditionDO edition) {
        if (!Integer.valueOf(EditionStatus.REGISTERING.getDatabaseValue()).equals(edition.getStatus())) {
            throw new BizException(
                    ErrorCode.INVALID_STATE_TRANSITION.getCode(),
                    "当前届次未开放报名"
            );
        }
        if (edition.getRegisterDeadline() == null
                || LocalDateTime.now(clock).isAfter(edition.getRegisterDeadline())) {
            throw new BizException(
                    ErrorCode.BUSINESS_DEADLINE_PASSED.getCode(),
                    "报名已截止"
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

    private SysUserDO requireAvailableTeacher(Long teacherId) {
        SysUserDO teacher = userMapper.selectById(teacherId);
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())) {
            throw invalidParam("指导教师不存在或已停用");
        }
        requireActiveSchool(teacher.getSchoolId());
        AuthorizationSnapshot snapshot = authorizationSnapshotService.loadRequired(teacherId);
        if (!snapshot.roleCodes().contains("TEACHER")) {
            throw invalidParam("所选用户不是指导教师");
        }
        return teacher;
    }

    private String normalizeRemark(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 500) {
            throw invalidParam("报名说明不能超过 500 个字符");
        }
        return normalized;
    }

    private List<RegistrationMemberSnapshot> memberSnapshots(List<TeamMemberDO> members) {
        return members.stream()
                .sorted(java.util.Comparator
                        .comparing((TeamMemberDO member) ->
                                Integer.valueOf(1).equals(member.getIsLeader()))
                        .reversed()
                        .thenComparing(TeamMemberDO::getId))
                .map(member -> new RegistrationMemberSnapshot(
                        member.getId(),
                        member.getUserId(),
                        member.getMemberName(),
                        member.getSchoolId(),
                        member.getStudentNo(),
                        Integer.valueOf(1).equals(member.getIsLeader())
                ))
                .toList();
    }

    private Map<String, Object> eligibilitySnapshot(
            RegistrationEligibilityResult result,
            LocalDateTime now
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("can_register", result.canRegister());
        snapshot.put("register_state", result.registerState());
        snapshot.put("reason", result.reason());
        snapshot.put("entry_grant_id", result.entryGrantId() == null
                ? null : String.valueOf(result.entryGrantId()));
        snapshot.put("quota_school_id", result.quotaSchoolId() == null
                ? null : String.valueOf(result.quotaSchoolId()));
        snapshot.put("quota_id", result.quotaId() == null
                ? null : String.valueOf(result.quotaId()));
        snapshot.put("max_teams", result.maxTeams());
        snapshot.put("used_teams", result.usedTeams());
        snapshot.put("captured_at", now.atZone(BUSINESS_ZONE)
                .toOffsetDateTime()
                .toString());
        return snapshot;
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), errorMessage);
        }
    }

    private BizException registrationConflict(RegistrationEligibilityResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("can_register", false);
        data.put("register_state", result.registerState());
        data.put("reason", result.reason());
        data.put("quota_school_id", result.quotaSchoolId() == null
                ? null : String.valueOf(result.quotaSchoolId()));
        data.put("quota_id", result.quotaId() == null
                ? null : String.valueOf(result.quotaId()));
        data.put("max_teams", result.maxTeams());
        data.put("used_teams", result.usedTeams());
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                result.reason() == null ? "当前队伍不满足报名资格" : result.reason(),
                data
        );
    }
    private RegistrationView registrationView(TeamDO team) {
        TeamStatus status = TeamStatus.fromDatabaseValue(team.getStatus());
        if (status == TeamStatus.PENDING_TEACHER
                || status == TeamStatus.PENDING_ADMIN
                || status == TeamStatus.APPROVED) {
            return new RegistrationView(false, "ALREADY_REGISTERED", "当前队伍已进入报名流程");
        }
        RegistrationEligibilityResult result = eligibilityService.checkCanRegister(
                new RegistrationEligibilityContext(
                        team.getId(),
                        team.getEditionId(),
                        null,
                        RegistrationCheckMode.SUBMIT
                )
        );
        return new RegistrationView(
                result.canRegister(),
                result.registerState(),
                result.reason()
        );
    }

    private CurrentRegistrationView currentRegistrationView(TeamDO team) {
        if (team.getCurrentRegistrationId() == null) {
            return CurrentRegistrationView.empty();
        }
        TeamRegistrationDO registration = registrationMapper.selectById(
                team.getCurrentRegistrationId()
        );
        if (registration == null) {
            return CurrentRegistrationView.empty();
        }
        TeamTeacherApplicationDO application =
                teacherApplicationMapper.selectCurrentByRegistrationId(registration.getId());
        Long teacherId = application == null
                ? team.getTeacherId()
                : application.getTeacherId();
        SysUserDO teacher = teacherId == null ? null : userMapper.selectById(teacherId);
        return new CurrentRegistrationView(
                registration.getVersion(),
                registration.getStatus(),
                registration.getRejectStage(),
                registration.getRejectReason(),
                teacherId == null ? null : String.valueOf(teacherId),
                teacher == null ? null : teacher.getRealName(),
                application == null ? null : application.getStatus()
        );
    }

    private String pendingTeacherTeamSql(Long userId) {
        return """
                SELECT tr.team_id
                  FROM team_registration tr
                  JOIN team_teacher_application app
                    ON app.registration_id = tr.id
                  JOIN team t
                    ON t.id = tr.team_id
                   AND t.current_registration_id = tr.id
                 WHERE app.teacher_id = %d
                   AND app.status IN ('PENDING', 'ACCEPTED')
                   AND app.deleted = 0
                   AND tr.deleted = 0
                   AND tr.status IN ('PENDING_TEACHER', 'PENDING_ADMIN', 'APPROVED')
                   AND t.deleted = 0
                """.formatted(userId);
    }

    private String organizationScopeEditionSql(java.util.Set<Long> organizationIds) {
        String ids = organizationIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        return """
                SELECT ce.id
                  FROM contest_edition ce
                  JOIN sys_user u ON u.id = team.leader_id
                  LEFT JOIN organization school ON school.id = u.school_id
                 WHERE ce.deleted = 0
                   AND u.deleted = 0
                   AND (
                     ce.owner_org_id IN (%s)
                     OR ce.school_id IN (%s)
                     OR u.school_id IN (%s)
                   )
                """.formatted(ids, ids, ids);
    }

    private String provinceScopeEditionSql(java.util.Set<Long> provinceCodes) {
        String codes = provinceCodes.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .map(code -> "'" + code + "'")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                SELECT ce.id
                  FROM contest_edition ce
                  JOIN sys_user u ON u.id = team.leader_id
                  LEFT JOIN organization school ON school.id = u.school_id
                 WHERE ce.deleted = 0
                   AND u.deleted = 0
                   AND (
                     ce.province_code IN (%s)
                     OR school.province_code IN (%s)
                   )
                """.formatted(codes, codes);
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
        if (!hasText(value)) {
            throw invalidParam(fieldName + " 不能为空");
        }
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

    private String normalizeKeyword(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw invalidParam("keyword 不能超过 64 个字符");
        }
        return normalized;
    }

    private int normalizeTeacherPageSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size < 1 || size > 50) {
            throw invalidParam("size 必须在 1-50 之间");
        }
        return size;
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

    private record CurrentRegistrationView(
            Integer version,
            String status,
            String rejectStage,
            String rejectReason,
            String teacherId,
            String teacherName,
            String teacherApplicationStatus
    ) {
        private static CurrentRegistrationView empty() {
            return new CurrentRegistrationView(
                    null, null, null, null, null, null, null
            );
        }
    }
}
