package com.campus.contest.module.team.service;

import com.campus.contest.module.team.dto.EntryGrantBatchRequest;
import com.campus.contest.module.team.vo.EntryGrantBatchVO;
import com.campus.contest.module.team.vo.EntryGrantListVO;

public interface EditionEntryGrantService {

    EntryGrantBatchVO batch(
            Long editionId,
            EntryGrantBatchRequest request,
            String idempotencyKey
    );

    EntryGrantListVO list(Long editionId, String status);
}
