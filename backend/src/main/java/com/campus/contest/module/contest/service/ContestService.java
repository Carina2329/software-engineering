package com.campus.contest.module.contest.service;

import com.campus.contest.module.contest.dto.ContestCreateRequest;
import com.campus.contest.module.contest.dto.ContestPageQuery;
import com.campus.contest.module.contest.dto.ContestUpdateRequest;
import com.campus.contest.module.contest.vo.ContestCreateVO;
import com.campus.contest.module.contest.vo.ContestDetailVO;
import com.campus.contest.module.contest.vo.ContestPageVO;
import com.campus.contest.module.contest.vo.ContestVersionVO;

public interface ContestService {

    ContestPageVO page(ContestPageQuery query);

    ContestDetailVO get(Long contestId);

    ContestCreateVO create(ContestCreateRequest request, String idempotencyKey);

    ContestVersionVO update(Long contestId, ContestUpdateRequest request, String idempotencyKey);
}
