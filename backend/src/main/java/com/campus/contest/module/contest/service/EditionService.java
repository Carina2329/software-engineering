package com.campus.contest.module.contest.service;

import com.campus.contest.module.contest.dto.EditionCreateRequest;
import com.campus.contest.module.contest.dto.EditionPageQuery;
import com.campus.contest.module.contest.dto.EditionPublishRequest;
import com.campus.contest.module.contest.dto.EditionUpdateRequest;
import com.campus.contest.module.contest.vo.EditionCreateVO;
import com.campus.contest.module.contest.vo.EditionDetailVO;
import com.campus.contest.module.contest.vo.EditionPageVO;
import com.campus.contest.module.contest.vo.EditionPublishVO;
import com.campus.contest.module.contest.vo.EditionVersionVO;

public interface EditionService {

    EditionPageVO page(Long contestId, EditionPageQuery query);

    EditionCreateVO create(Long contestId, EditionCreateRequest request, String idempotencyKey);

    EditionDetailVO get(Long editionId);

    EditionVersionVO update(Long editionId, EditionUpdateRequest request, String idempotencyKey);

    EditionPublishVO publish(Long editionId, EditionPublishRequest request, String idempotencyKey);
}
