package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * EP-004 PATCH /api/v1/users/me 请求体。
 *
 * 只暴露个人档案冻结字段：real_name、student_no、college、major、grade、title 和 version。
 * username、password_hash、email、status、角色和权限都不在本 DTO 中声明，
 * 一旦客户端提交这些字段，会被 {@link #collectUnexpectedField(String, Object)} 收集，
 * 由 Service 统一返回 400 + 10001，避免“字段被静默忽略”造成越权假象。
 *
 * 同时记录“请求体里实际出现了哪些字段”（presentFields）：
 * 这与“字段值是否为 null”是两件事。例如教师提交 {"student_no":""} 或 {"student_no":null}，
 * 字段确实出现了，必须按角色越界返回 400，而不是当成“没提交”静默忽略。
 */
public class ProfileUpdateRequest {

    public static final String FIELD_REAL_NAME = "real_name";
    public static final String FIELD_STUDENT_NO = "student_no";
    public static final String FIELD_COLLEGE = "college";
    public static final String FIELD_MAJOR = "major";
    public static final String FIELD_GRADE = "grade";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_VERSION = "version";

    private String realName;
    private String studentNo;
    private String college;
    private String major;
    private String grade;
    private String title;
    private Integer version;

    /** 请求体中出现过的档案字段名，用于区分“未提交”和“提交了空值” */
    private final Set<String> presentFields = new LinkedHashSet<>();

    /** 请求体中出现但不在档案白名单内的字段名，仅用于拒绝越界修改 */
    private final List<String> unexpectedFields = new ArrayList<>();

    @JsonProperty(FIELD_REAL_NAME)
    public String getRealName() {
        return realName;
    }

    @JsonProperty(FIELD_REAL_NAME)
    public void setRealName(String realName) {
        presentFields.add(FIELD_REAL_NAME);
        this.realName = realName;
    }

    @JsonProperty(FIELD_STUDENT_NO)
    public String getStudentNo() {
        return studentNo;
    }

    @JsonProperty(FIELD_STUDENT_NO)
    public void setStudentNo(String studentNo) {
        presentFields.add(FIELD_STUDENT_NO);
        this.studentNo = studentNo;
    }

    @JsonProperty(FIELD_COLLEGE)
    public String getCollege() {
        return college;
    }

    @JsonProperty(FIELD_COLLEGE)
    public void setCollege(String college) {
        presentFields.add(FIELD_COLLEGE);
        this.college = college;
    }

    @JsonProperty(FIELD_MAJOR)
    public String getMajor() {
        return major;
    }

    @JsonProperty(FIELD_MAJOR)
    public void setMajor(String major) {
        presentFields.add(FIELD_MAJOR);
        this.major = major;
    }

    @JsonProperty(FIELD_GRADE)
    public String getGrade() {
        return grade;
    }

    @JsonProperty(FIELD_GRADE)
    public void setGrade(String grade) {
        presentFields.add(FIELD_GRADE);
        this.grade = grade;
    }

    @JsonProperty(FIELD_TITLE)
    public String getTitle() {
        return title;
    }

    @JsonProperty(FIELD_TITLE)
    public void setTitle(String title) {
        presentFields.add(FIELD_TITLE);
        this.title = title;
    }

    @JsonProperty(FIELD_VERSION)
    public Integer getVersion() {
        return version;
    }

    @JsonProperty(FIELD_VERSION)
    public void setVersion(Integer version) {
        this.version = version;
    }

    /** 该字段是否在请求体中出现过（值可以为 null 或空串） */
    @JsonIgnore
    public boolean hasField(String field) {
        return presentFields.contains(field);
    }

    /** 请求体中出现的全部字段名（含越界字段），用于审计与错误提示 */
    @JsonIgnore
    public Set<String> getPresentFields() {
        return presentFields;
    }

    @JsonIgnore
    public List<String> getUnexpectedFields() {
        return unexpectedFields;
    }

    @JsonAnySetter
    public void collectUnexpectedField(String name, Object value) {
        unexpectedFields.add(name);
    }
}
