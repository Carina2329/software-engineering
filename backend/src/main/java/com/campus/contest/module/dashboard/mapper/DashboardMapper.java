package com.campus.contest.module.dashboard.mapper;

import com.campus.contest.module.dashboard.dto.DashboardDeadlineRow;
import com.campus.contest.module.dashboard.dto.DashboardLevelCountRow;
import com.campus.contest.module.dashboard.vo.DashboardProvinceSchoolVO;
import com.campus.contest.module.dashboard.vo.DashboardUserRoleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("""
            SELECT COUNT(DISTINCT o.id)
            FROM organization o
            JOIN sys_dict_item di
              ON di.item_value = o.province_code
             AND di.status = 1
             AND di.deleted = 0
            JOIN sys_dict_type dt
              ON dt.id = di.dict_type_id
             AND dt.dict_code = 'CN_PROVINCE'
             AND dt.status = 1
             AND dt.deleted = 0
            WHERE o.org_type = 'SCHOOL'
              AND o.status = 1
              AND o.deleted = 0
            """)
    long countEffectiveSchools();

    @Select("SELECT COUNT(1) FROM sys_user WHERE status = 1 AND deleted = 0")
    long countActiveUsers();

    @Select("""
            SELECT COUNT(1)
            FROM contest_edition
            WHERE deleted = 0
              AND status = 1
              AND register_deadline >= #{now}
            """)
    long countRegisteringEditions(@Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(1)
            FROM sys_user
            WHERE status = 1
              AND deleted = 0
              AND school_id IS NOT NULL
              AND school_verification_status = 'PENDING'
            """)
    long countPendingAffiliations();

    @Select("SELECT COUNT(1) FROM contest WHERE deleted = 0")
    long countContestSeries();

    @Select("SELECT COUNT(1) FROM contest_edition WHERE deleted = 0 AND status <> 0")
    long countPublishedEditions();

    @Select("""
            SELECT level, COUNT(1) AS count
            FROM contest_edition
            WHERE deleted = 0
              AND level IN ('SCHOOL', 'PROVINCE', 'NATIONAL')
            GROUP BY level
            """)
    List<DashboardLevelCountRow> selectLevelDistribution();

    @Select("""
            SELECT o.province_code,
                   di.item_label AS province_name,
                   COUNT(DISTINCT o.id) AS school_count
            FROM organization o
            JOIN sys_dict_item di
              ON di.item_value = o.province_code
             AND di.status = 1
             AND di.deleted = 0
            JOIN sys_dict_type dt
              ON dt.id = di.dict_type_id
             AND dt.dict_code = 'CN_PROVINCE'
             AND dt.status = 1
             AND dt.deleted = 0
            WHERE o.org_type = 'SCHOOL'
              AND o.status = 1
              AND o.deleted = 0
            GROUP BY o.province_code, di.item_label
            ORDER BY school_count DESC, o.province_code ASC
            """)
    List<DashboardProvinceSchoolVO> selectProvinceSchools();

    @Select("""
            SELECT r.role_code,
                   r.role_name,
                   COUNT(DISTINCT u.id) AS user_count
            FROM sys_role r
            JOIN sys_user_role ur
              ON ur.role_id = r.id
             AND ur.status = 1
             AND ur.deleted = 0
             AND (ur.valid_from IS NULL OR ur.valid_from <= #{now})
             AND (ur.valid_to IS NULL OR ur.valid_to > #{now})
            JOIN sys_user u
              ON u.id = ur.user_id
             AND u.status = 1
             AND u.deleted = 0
            WHERE r.deleted = 0
            GROUP BY r.id, r.role_code, r.role_name
            ORDER BY r.id
            """)
    List<DashboardUserRoleVO> selectUserRoles(@Param("now") LocalDateTime now);

    @Select("""
            SELECT e.id AS edition_id,
                   e.edition_name,
                   c.contest_name,
                   e.level,
                   e.register_deadline
            FROM contest_edition e
            JOIN contest c ON c.id = e.contest_id
            WHERE e.deleted = 0
              AND e.status = 1
              AND e.register_deadline >= #{now}
              AND e.register_deadline < #{end}
            ORDER BY e.register_deadline ASC, e.id ASC
            LIMIT #{limit}
            """)
    List<DashboardDeadlineRow> selectUpcomingDeadlines(
            @Param("now") LocalDateTime now,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit
    );
}
