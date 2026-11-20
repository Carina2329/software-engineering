package com.campus.contest.module.contest.mapper;

import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class ContestAccessSqlProvider {

    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
            "c.created_at",
            "c.contest_name",
            "c.level",
            "e.created_at",
            "e.edition_name",
            "e.year",
            "e.status"
    );

    public String selectContestPage(ContestAccessCriteria criteria) {
        return contestBase(criteria) + order(criteria, "c.created_at");
    }

    public String selectContestById(ContestAccessCriteria criteria) {
        return contestBase(criteria) + " AND c.id = #{criteria.contestId}";
    }

    public String selectEditionList(ContestAccessCriteria criteria) {
        return editionBase(criteria) + order(criteria, "e.created_at");
    }

    public String selectEditionPage(ContestAccessCriteria criteria) {
        return editionBase(criteria) + order(criteria, "e.created_at");
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
        sql.append(" AND ").append(contestAccessExpression(criteria));
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
        sql.append(" AND ").append(editionAccessExpression(criteria, "e"));
        return sql.toString();
    }

    private String contestAccessExpression(ContestAccessCriteria criteria) {
        String alias = "visible_edition";
        String expression = criteria.isManager()
                ? managerScopeExpression(criteria, alias)
                : ordinaryVisibilityExpression(criteria, alias);
        return "EXISTS (SELECT 1 FROM contest_edition " + alias
                + " WHERE " + alias + ".deleted = 0 AND " + expression
                + " AND " + alias + ".contest_id = c.id)";
    }

    private String editionAccessExpression(ContestAccessCriteria criteria, String alias) {
        return criteria.isManager()
                ? managerScopeExpression(criteria, alias)
                : ordinaryVisibilityExpression(criteria, alias);
    }

    private String ordinaryVisibilityExpression(ContestAccessCriteria criteria, String alias) {
        StringBuilder expression = new StringBuilder(alias)
                .append(".status <> 0 AND (")
                .append(alias).append(".visibility = 'PUBLIC'");
        if (criteria.getViewerSchoolId() != null) {
            expression.append(" OR (").append(alias).append(".visibility = 'SCHOOL' AND ")
                    .append(alias).append(".school_id = #{criteria.viewerSchoolId})");
        }
        if (criteria.getViewerProvinceCode() != null) {
            expression.append(" OR (").append(alias).append(".visibility = 'PROVINCE' AND ")
                    .append(alias).append(".province_code = CONCAT('', ")
                    .append("#{criteria.viewerProvinceCode}))");
        }
        if (!criteria.getEventIds().isEmpty()) {
            expression.append(" OR (").append(alias).append(".visibility = 'UNLISTED' AND ")
                    .append(alias).append(".id IN (");
            appendPlaceholders(expression, criteria.getEventIds(), "eventIds");
            expression.append("))");
        }
        return expression.append(")").toString();
    }

    private String managerScopeExpression(ContestAccessCriteria criteria, String alias) {
        if (criteria.isGlobal()) {
            return "1 = 1";
        }
        StringBuilder expression = new StringBuilder("(1 = 0");
        appendIn(
                expression,
                criteria.getOrganizationIds(),
                alias,
                "owner_org_id",
                "organizationIds"
        );
        appendIn(
                expression,
                criteria.getProvinceCodes(),
                alias,
                "province_code",
                "provinceCodes"
        );
        appendIn(expression, criteria.getEventIds(), alias, "id", "eventIds");
        return expression.append(")").toString();
    }

    private void appendIn(
            StringBuilder sql,
            List<Long> values,
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
            List<Long> values,
            String property
    ) {
        StringJoiner placeholders = new StringJoiner(", ");
        for (int index = 0; index < values.size(); index++) {
            placeholders.add("#{criteria." + property + "[" + index + "]}");
        }
        sql.append(placeholders);
    }

    private String order(ContestAccessCriteria criteria, String defaultColumn) {
        String column = hasText(criteria.getSortColumn())
                && ALLOWED_SORT_COLUMNS.contains(criteria.getSortColumn())
                ? criteria.getSortColumn()
                : defaultColumn;
        String direction = "ASC".equals(criteria.getSortDirection()) ? "ASC" : "DESC";
        return " ORDER BY " + column + " " + direction;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
