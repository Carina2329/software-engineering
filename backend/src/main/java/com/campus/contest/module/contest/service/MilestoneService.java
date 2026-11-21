package com.campus.contest.module.contest.service;

import com.campus.contest.module.contest.dto.MilestoneCreateRequest;
import com.campus.contest.module.contest.dto.MilestoneUpdateRequest;
import com.campus.contest.module.contest.vo.MilestoneCreateVO;
import com.campus.contest.module.contest.vo.MilestoneVO;
import com.campus.contest.module.contest.vo.MilestoneVersionVO;

import java.util.List;

public interface MilestoneService {

    List<MilestoneVO> list(Long editionId);

    MilestoneCreateVO create(Long editionId, MilestoneCreateRequest request, String idempotencyKey);

    MilestoneVersionVO update(Long milestoneId, MilestoneUpdateRequest request, String idempotencyKey);
}
