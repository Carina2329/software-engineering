package com.campus.contest.module.organization.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SchoolAffiliationMapper {

    @Update("""
            UPDATE sys_user
            SET school_verification_status = #{status},
                school_reviewed_by = #{reviewedBy},
                school_reviewed_at = #{reviewedAt},
                school_review_reason = #{reason},
                updated_at = #{reviewedAt},
                updated_by = #{reviewedBy},
                version = version + 1
            WHERE id = #{userId}
              AND version = #{version}
              AND school_verification_status = 'PENDING'
              AND deleted = 0
            """)
    int updateReview(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("reviewedBy") Long reviewedBy,
            @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("reason") String reason,
            @Param("version") Integer version
    );
}
