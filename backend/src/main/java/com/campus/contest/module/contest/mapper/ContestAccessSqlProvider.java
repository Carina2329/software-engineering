package com.campus.contest.module.contest.mapper;

import java.util.Set;
import java.util.StringJoiner;

public class ContestAccessSqlProvider {

    public String selectContestPage(ContestAccessCriteria criteria) {
        return contestBase(criteria) + order(criteria);
    }

    public String selectContestById(ContestAccessCriteria criteria) {
        return contestBase(criteria) + " AND c.id = #{criteria.contestId}";
    }

    public String selectEditionList(ContestAccessCriteria criteria) {
        return editionBase(criteria) + order(criteria);
    }

    public String selectEditionPage(ContestAccessCriteria criteria) {
        return editionBase(criteria) + order(criteria);
    }

    public String selectEditionById(ContestAccessCriteria criteria) {
        return editionBase(criteria) + " AND e.id = #{criteria.editionId}";
    }

    private String contestBase(ContestAccessCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.*
                FROM contest c
                WHERE c.deleted = 0
                """);
        if (hasText(criteria.getKeyword())) {
            sql.append("""
                     AND (c.contest_name LIKE CONCAT('%', #{criteria.keyword}, '%')
                       OR c.organizer LIKE CONCAT('%', #{criteria.keyword}, '%'))
                    """);
        }
        if (hasText(criteria.getSeriesLevel())) {
            sql.append(" AND c.level = #{criteria.seriesLevel}");
        }
        sql.append(" AND ").append(contestAccessCondition(criteria, "c"));
        return sql.toString();
    }

    private String editionBase(ContestAccessCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.*
                FROM contest_edition e
                WHERE e.deleted = 0
                """);
        if (criteria.getContestId() != null) {
            sql.append(" AND e.contest_id = #{criteria.contestId}");
        }
        if (criteria.getYear() != null) {
            sql.append(" AND e.year = #{criteria.year}");
        }
        if (criteria.getStatus() != null) {
            sql.append(" AND e.status = #{criteria.status}");
        }
        if (hasText(criteria.getLevel())) {
            sql.append(" AND e.level = #{criteria.level}");
        }
        if (criteria.getOwnerOrgId() != null) {
            sql.append(" AND e.owner_org_id = #{criteria.ownerOrgId}");
        }
        if (hasText(criteria.getProvinceCode())) {
            sql.append(" AND e.province_code = #{criteria.provinceCode}");
        }
        if (criteria.getSchoolId() != null) {
            sql.append(" AND e.school_id = #{criteria.schoolId}");
        }
        if (hasText(criteria.getVisibility())) {
            sql.append(" AND e.visibility = #{criteria.visibility}");
        }
        sql.append(" AND ").append(editionAccessCondition(criteria, "e"));
        return sql.toString();
    }

    private String contestAccessCondition(ContestAccessCriteria criteria, String alias) {
        if (!criteria.isManager()) {
            return "EXISTS (" + ordinaryEditionCondition(criteria, "visible_edition")
                    + " AND visible_edition.contest_id = " + alias + ".id)";
        }
        if (criteria.isGlobal()) {
            return "1 = 1";
        }
        return "EXISTS (" + managerEditionCondition(criteria, "visible_edition")
                + " AND visible_edition.contest_id = " + alias + ".id)";
    }

    private String editionAccessCondition(ContestAccessCriteria criteria, String alias) {
        if (!criteria.isManager()) {
            String ordinary = ordinaryEditionCondition(criteria, alias);
            return ordinary.substring("WHERE ".length()).replace(alias + ".deleted = 0 AND ", "");
        }
        if (criteria.isGlobal()) {
            return "1 = 1";
        }
        return managerEditionCondition(criteria, alias)
                .substring("WHERE ".length())
                .replace(alias + ".deleted = 0 AND ", "");
    }

    private String ordinaryEditionCondition(ContestAccessCriteria criteria, String alias) {
        StringBuilder condition = new StringBuilder(
                "SELECT 1 FROM contest_edition " + alias
                        + " WHERE " + alias + ".deleted = 0 AND " + alias + ".status <> 0 AND ("
        );
        condition.append(alias).append(".visibility = 'PUBLIC'");
        if (criteria.getViewerSchoolId() != null) {
            condition.append(" OR (").append(alias).append(".visibility = 'SCHOOL' AND ")
                    .append(alias).append(".school_id = #{criteria.viewerSchoolId})");
        }
        if (criteria.getViewerProvinceCode() != null) {
            condition.append(" OR (").append(alias).append(".visibility = 'PROVINCE' AND ")
                    .append(alias).append(".province_code = ")
                    .append("CONCAT('', #{criteria.viewerProvinceCode}))");
        }
        if (!criteria.getEventIds().isEmpty()) {
            condition.append(" OR (").append(alias).append(".visibility = 'UNLISTED' AND ")
                    .append(alias).append(".id IN (");
            appendPlaceholders(condition, criteria.getEventIds(), "eventIds");
            condition.append("))");
        }
        condition.append(")");
        return condition.toString();
    }

    private String managerEditionCondition(ContestAccessCriteria criteria, String alias) {
        StringBuilder condition = new StringBuilder(
                "SELECT 1 FROM contest_edition " + alias
                        + " WHERE " + alias + ".deleted = 0 AND (1 = 0"
        );
        appendIn(condition, criteria.getOrganizationIds(), alias, "owner_org_id", "organizationIds");
        appendIn(condition, criteria.getProvinceCodes(), alias, "province_code", "provinceCodes");
        appendIn(condition, criteria.getEventIds(), alias, "id", "eventIds");
        condition.append(")");
        return condition.toString();
    }

    private void appendIn(
            StringBuilder sql,
            Set<Long> values,
            String alias,
            String column,
            String property
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sql.append(" OR ").append(alias).append(".").append(column).append(" IN (");
        appendPlaceholders(sql, values, property);
        sql.append(")");
    }

    private void appendPlaceholders(
            StringBuilder sql,
            Set<Long> values,
            String property
    ) {
        StringJoiner placeholders = new StringJoiner(", ");
        int index = 0;
        for (Long value : values) {
            String path = "criteria." + property + "[" + index++ + "]";
            placeholders.add("#{" + path + "}");
        }
        sql.append(placeholders);
    }

    private String order(ContestAccessCriteria criteria) {
        String column = criteria.getSortColumn();
        String direction = "ASC".equals(criteria.getSortDirection()) ? "ASC" : "DESC";
        return " ORDER BY " + column + " " + direction;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
