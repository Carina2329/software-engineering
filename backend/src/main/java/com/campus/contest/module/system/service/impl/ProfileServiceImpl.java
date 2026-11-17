package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.dto.ProfileUpdateRequest;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.ProfileService;
import com.campus.contest.module.system.vo.CurrentUserVO;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 个人档案实现（BIZ-PROFILE-001）。
 *
 * 关键约束：
 * 1. 只处理当前登录用户，接口不接受目标用户 ID，水平越权没有入口；
 * 2. 角色边界以 AuthorizationSnapshotService 的服务端角色快照为准，不信任前端隐藏输入框；
 * 3. 更新走 sys_user 上的 @Version 乐观锁，版本落后返回 HTTP 409 + 20001；
 * 4. 学号依赖 uk_sys_user_student_no 全平台唯一，写库前预检、写库时兜底捕获唯一键冲突；
 * 5. 每次成功修改写一条审计日志，记录操作人、变更字段、追踪号和结果，不含密码、令牌等敏感数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private static final int REAL_NAME_MAX_LENGTH = 32;
    private static final int STUDENT_NO_MAX_LENGTH = 20;
    private static final int COLLEGE_MAX_LENGTH = 64;
    private static final int MAJOR_MAX_LENGTH = 64;
    private static final int GRADE_MAX_LENGTH = 8;
    private static final int TITLE_MAX_LENGTH = 32;

    private static final String PROFILE_STUDENT = "STUDENT";
    private static final String PROFILE_TEACHER = "TEACHER";
    private static final String PROFILE_COMMON = "COMMON";

    /** 与 EP-003 user_type 的推导顺序保持一致，避免同一个账号在查询和修改时被判定成两种角色 */
    private static final List<String> TYPE_PRIORITY = List.of("ADMIN", "TEACHER", "REVIEWER", "STUDENT");

    private static final String MODULE = "PROFILE";
    private static final String OPERATION_UPDATE = "USER_PROFILE_UPDATE";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private static final String FIELD_REAL_NAME = "real_name";
    private static final String FIELD_STUDENT_NO = "student_no";
    private static final String FIELD_COLLEGE = "college";
    private static final String FIELD_MAJOR = "major";
    private static final String FIELD_GRADE = "grade";
    private static final String FIELD_TITLE = "title";

    private final SysUserMapper userMapper;
    private final AuthorizationSnapshotService authorizationSnapshotService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final AuditLogService auditLogService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // EP-003 档案字段适配
    // ------------------------------------------------------------------

    @Override
    public void applyProfileFields(CurrentUserVO vo) {
        if (vo == null) {
            return;
        }
        Long userId = currentUserProvider.requireUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(
                    ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    ErrorCode.RESOURCE_NOT_FOUND.getMessage()
            );
        }
        String profileType = profileTypeOfUserType(vo.getUserType());
        vo.setVersion(user.getVersion() == null ? 0 : user.getVersion());
        // 只返回当前角色可维护的字段，其余字段置空，避免教师页面拿学生字段、学生页面拿职称
        vo.setCollege(user.getCollege());
        if (PROFILE_STUDENT.equals(profileType)) {
            vo.setStudentNo(user.getStudentNo());
            vo.setMajor(user.getMajor());
            vo.setGrade(user.getGrade());
            vo.setTitle(null);
        } else {
            vo.setStudentNo(null);
            vo.setMajor(null);
            vo.setGrade(null);
            vo.setTitle(PROFILE_TEACHER.equals(profileType) ? user.getTitle() : null);
        }
    }

    // ------------------------------------------------------------------
    // EP-004 修改本人档案
    // ------------------------------------------------------------------

    @Override
    public IdVersionVO updateMyProfile(ProfileUpdateRequest request, String idempotencyKey) {
        Long userId = currentUserProvider.requireUserId();
        long startedAt = System.currentTimeMillis();
        try {
            String key = normalizeIdempotencyKey(idempotencyKey);
            if (key == null) {
                // 未携带 Idempotency-Key 时仍由 version 乐观锁保证不会重复生效
                return doUpdateMyProfile(userId, request, startedAt);
            }
            return idempotencyService.execute(
                    new IdempotencyScope(IdempotencyScope.PrincipalType.USER, String.valueOf(userId)),
                    OPERATION_UPDATE,
                    key,
                    request,
                    IDEMPOTENCY_TTL,
                    IdVersionVO.class,
                    () -> doUpdateMyProfile(userId, request, startedAt)
            );
        } catch (BizException e) {
            recordFailureAudit(userId, request, e, startedAt);
            throw e;
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > 64) {
            throw invalidParam("Idempotency-Key 必须为 1-64 位非空字符串");
        }
        return key;
    }

    private IdVersionVO doUpdateMyProfile(Long userId, ProfileUpdateRequest request, long startedAt) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        rejectUnexpectedFields(request);
        Integer version = request.getVersion();
        if (version == null || version < 0) {
            throw invalidParam("version 必须为非负整数");
        }

        SysUserDO existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BizException(
                    ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    ErrorCode.RESOURCE_NOT_FOUND.getMessage()
            );
        }

        Set<String> roleCodes = authorizationSnapshotService.loadRequired(userId).roleCodes();
        String profileType = profileTypeOfRoles(roleCodes);

        Map<String, String> changes = collectChanges(request, profileType);
        if (changes.isEmpty()) {
            throw invalidParam("至少提交一个档案字段");
        }

        String newStudentNo = changes.get(FIELD_STUDENT_NO);
        if (newStudentNo != null && !Objects.equals(newStudentNo, existing.getStudentNo())) {
            long conflicts = userMapper.countByStudentNoExcluding(newStudentNo, userId);
            if (conflicts > 0) {
                throw duplicateStudentNo();
            }
        }

        SysUserDO update = buildUpdateEntity(userId, version, changes);
        Integer newVersion = transactionTemplate.execute(status -> {
            int rows;
            try {
                rows = userMapper.updateById(update);
            } catch (DataIntegrityViolationException e) {
                // 并发写入同一学号时，唯一索引是最后一道防线；这里转成明确业务错误而不是 500
                if (changes.containsKey(FIELD_STUDENT_NO)) {
                    throw duplicateStudentNo();
                }
                throw e;
            }
            if (rows != 1) {
                throw versionConflict();
            }
            return version + 1;
        });
        if (newVersion == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "档案更新事务未返回结果");
        }

        recordSuccessAudit(userId, existing, changes, version, newVersion, startedAt);
        return new IdVersionVO(String.valueOf(userId), newVersion);
    }

    // ------------------------------------------------------------------
    // 参数校验与字段边界
    // ------------------------------------------------------------------

    private void rejectUnexpectedFields(ProfileUpdateRequest request) {
        List<String> unexpected = request.getUnexpectedFields();
        if (unexpected != null && !unexpected.isEmpty()) {
            throw invalidParam("不允许通过个人档案接口修改字段：" + String.join("、", unexpected));
        }
    }

    /**
     * 按角色收集本次真正提交的字段，并在收集过程中完成角色边界和长度校验。
     * 只有出现在请求体中的字段才会进入结果，未出现的字段保持库内原值。
     */
    private Map<String, String> collectChanges(ProfileUpdateRequest request, String profileType) {
        Map<String, String> changes = new LinkedHashMap<>();

        putIfPresent(changes, FIELD_REAL_NAME, request.getRealName(), REAL_NAME_MAX_LENGTH, "姓名");
        putIfPresent(changes, FIELD_COLLEGE, request.getCollege(), COLLEGE_MAX_LENGTH, "学院");

        if (PROFILE_STUDENT.equals(profileType)) {
            requireRoleAllowed(request.getTitle(), "学生档案不允许修改职称");
            putIfPresent(changes, FIELD_STUDENT_NO, request.getStudentNo(), STUDENT_NO_MAX_LENGTH, "学号");
            putIfPresent(changes, FIELD_MAJOR, request.getMajor(), MAJOR_MAX_LENGTH, "专业");
            putIfPresent(changes, FIELD_GRADE, request.getGrade(), GRADE_MAX_LENGTH, "年级");
        } else if (PROFILE_TEACHER.equals(profileType)) {
            requireRoleAllowed(request.getStudentNo(), "教师档案不允许修改学号");
            requireRoleAllowed(request.getMajor(), "教师档案不允许修改专业");
            requireRoleAllowed(request.getGrade(), "教师档案不允许修改年级");
            putIfPresent(changes, FIELD_TITLE, request.getTitle(), TITLE_MAX_LENGTH, "职称");
        } else {
            requireRoleAllowed(request.getStudentNo(), "当前角色不允许修改学号");
            requireRoleAllowed(request.getMajor(), "当前角色不允许修改专业");
            requireRoleAllowed(request.getGrade(), "当前角色不允许修改年级");
            requireRoleAllowed(request.getTitle(), "当前角色不允许修改职称");
        }
        return changes;
    }

    /** 角色越界：只要提交了非本人角色的字段就返回参数错误，而不是静默忽略 */
    private void requireRoleAllowed(String value, String message) {
        if (StringUtils.hasText(value)) {
            throw invalidParam(message);
        }
    }

    private void putIfPresent(
            Map<String, String> changes,
            String field,
            String rawValue,
            int maxLength,
            String label
    ) {
        if (rawValue == null) {
            return;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw invalidParam(label + "不能为空");
        }
        if (value.length() > maxLength) {
            throw invalidParam(label + "长度不能超过 " + maxLength + " 个字符");
        }
        if (FIELD_STUDENT_NO.equals(field) && value.chars().anyMatch(Character::isWhitespace)) {
            throw invalidParam("学号不能包含空格");
        }
        changes.put(field, value);
    }

    // ------------------------------------------------------------------
    // 写库与审计
    // ------------------------------------------------------------------

    private SysUserDO buildUpdateEntity(Long userId, Integer version, Map<String, String> changes) {
        SysUserDO update = new SysUserDO();
        update.setId(userId);
        update.setVersion(version);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(userId);
        if (changes.containsKey(FIELD_REAL_NAME)) {
            update.setRealName(changes.get(FIELD_REAL_NAME));
        }
        if (changes.containsKey(FIELD_STUDENT_NO)) {
            update.setStudentNo(changes.get(FIELD_STUDENT_NO));
        }
        if (changes.containsKey(FIELD_COLLEGE)) {
            update.setCollege(changes.get(FIELD_COLLEGE));
        }
        if (changes.containsKey(FIELD_MAJOR)) {
            update.setMajor(changes.get(FIELD_MAJOR));
        }
        if (changes.containsKey(FIELD_GRADE)) {
            update.setGrade(changes.get(FIELD_GRADE));
        }
        if (changes.containsKey(FIELD_TITLE)) {
            update.setTitle(changes.get(FIELD_TITLE));
        }
        return update;
    }

    private void recordSuccessAudit(
            Long userId,
            SysUserDO before,
            Map<String, String> changes,
            Integer versionBefore,
            Integer versionAfter,
            long startedAt
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("changed_fields", new ArrayList<>(changes.keySet()));
        params.put("before", beforeValues(before, changes.keySet()));
        params.put("after", new LinkedHashMap<>(changes));
        params.put("version_before", versionBefore);
        params.put("version_after", versionAfter);
        params.put("result", "SUCCESS");
        recordAudit(userId, params, 0, startedAt);
    }

    private void recordFailureAudit(Long userId, ProfileUpdateRequest request, BizException failure, long startedAt) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("changed_fields", requestedFieldNames(request));
        params.put("result", "REJECTED");
        params.put("reason", failure.getMessage());
        recordAudit(userId, params, failure.getCode(), startedAt);
    }

    private List<String> requestedFieldNames(ProfileUpdateRequest request) {
        List<String> names = new ArrayList<>();
        if (request == null) {
            return names;
        }
        if (request.getRealName() != null) {
            names.add(FIELD_REAL_NAME);
        }
        if (request.getStudentNo() != null) {
            names.add(FIELD_STUDENT_NO);
        }
        if (request.getCollege() != null) {
            names.add(FIELD_COLLEGE);
        }
        if (request.getMajor() != null) {
            names.add(FIELD_MAJOR);
        }
        if (request.getGrade() != null) {
            names.add(FIELD_GRADE);
        }
        if (request.getTitle() != null) {
            names.add(FIELD_TITLE);
        }
        return names;
    }

    private Map<String, Object> beforeValues(SysUserDO user, Set<String> fields) {
        Map<String, Object> before = new LinkedHashMap<>();
        for (String field : fields) {
            switch (field) {
                case FIELD_REAL_NAME -> before.put(field, user.getRealName());
                case FIELD_STUDENT_NO -> before.put(field, user.getStudentNo());
                case FIELD_COLLEGE -> before.put(field, user.getCollege());
                case FIELD_MAJOR -> before.put(field, user.getMajor());
                case FIELD_GRADE -> before.put(field, user.getGrade());
                case FIELD_TITLE -> before.put(field, user.getTitle());
                default -> {
                    // 白名单外的字段不会进入审计
                }
            }
        }
        return before;
    }

    private void recordAudit(Long userId, Map<String, Object> params, Integer result, long startedAt) {
        try {
            auditLogService.record(new AuditLogCommand(
                    userId,
                    MODULE,
                    OPERATION_UPDATE,
                    requestUri(),
                    requestMethod(),
                    params(params),
                    clientIp(),
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt)),
                    result == null ? 0 : result,
                    null
            ));
        } catch (RuntimeException e) {
            // 审计写入失败不改变已经提交的档案结果，只记录告警
            log.warn("个人档案审计写入失败: operation={}, error={}", OPERATION_UPDATE, e.getClass().getSimpleName());
        }
    }

    private String params(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("个人档案审计参数序列化失败: {}", e.getClass().getSimpleName());
            return "{}";
        }
    }

    // ------------------------------------------------------------------
    // 角色判定与请求上下文
    // ------------------------------------------------------------------

    private String profileTypeOfUserType(String userType) {
        if (PROFILE_STUDENT.equalsIgnoreCase(userType == null ? "" : userType.trim())) {
            return PROFILE_STUDENT;
        }
        if (PROFILE_TEACHER.equalsIgnoreCase(userType == null ? "" : userType.trim())) {
            return PROFILE_TEACHER;
        }
        return PROFILE_COMMON;
    }

    /** 复用 EP-003 的角色优先级：ADMIN > TEACHER > REVIEWER > STUDENT */
    private String profileTypeOfRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return PROFILE_COMMON;
        }
        for (String candidate : TYPE_PRIORITY) {
            if (roleCodes.contains(candidate)) {
                return switch (candidate) {
                    case "TEACHER" -> PROFILE_TEACHER;
                    case "STUDENT" -> PROFILE_STUDENT;
                    default -> PROFILE_COMMON;
                };
            }
        }
        return PROFILE_COMMON;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String requestUri() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRequestURI();
    }

    private String requestMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod();
    }

    private String clientIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRemoteAddr();
    }

    // ------------------------------------------------------------------
    // 业务异常
    // ------------------------------------------------------------------

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException versionConflict() {
        return new BizException(ErrorCode.BUSINESS_CONFLICT.getCode(), "资料已被修改，请刷新后重试");
    }

    private BizException duplicateStudentNo() {
        return new BizException(ErrorCode.DUPLICATE_RELATION.getCode(), "学号已存在，请使用本人唯一学号");
    }
}
