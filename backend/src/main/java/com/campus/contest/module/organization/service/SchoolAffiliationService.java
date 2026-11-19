package com.campus.contest.module.organization.service;

import com.campus.contest.module.organization.dto.SchoolAffiliationPageQuery;
import com.campus.contest.module.organization.dto.SchoolAffiliationReviewRequest;
import com.campus.contest.module.organization.model.SchoolAffiliationState;
import com.campus.contest.module.organization.model.SchoolSelectionValidation;
import com.campus.contest.module.organization.vo.SchoolAffiliationPageVO;
import com.campus.contest.module.organization.vo.SchoolAffiliationReviewVO;

public interface SchoolAffiliationService {

    SchoolAffiliationPageVO pageAffiliations(SchoolAffiliationPageQuery query);

    SchoolAffiliationReviewVO reviewAffiliation(
            Long userId,
            SchoolAffiliationReviewRequest request,
            String idempotencyKey
    );

    SchoolAffiliationState getState(Long userId);

    /**
     * 供 EP-004 档案任务调用；不写数据库、不写审计，由档案任务完成最终更新与审计。
     */
    SchoolSelectionValidation validateSelection(Long userId, Long schoolId, Integer version);
}
