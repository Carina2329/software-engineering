package com.campus.contest.module.work.service;

import com.campus.contest.module.work.dto.UploadDtos;

/**
 * 分片上传能力（EP-046 ~ EP-049）。
 *
 * init 与 merge 在 OpenAPI 中把 Idempotency-Key 标为必填，因此把 key 显式传进来，
 * 由实现类统一校验并在幂等服务中复用同一 key 的已有结果。
 */
public interface UploadService {

    UploadDtos.InitResponse init(UploadDtos.InitRequest request, String idempotencyKey);

    UploadDtos.ChunkResponse uploadChunk(Long uploadId, Integer chunkIndex, String expectedSha256, byte[] body);

    UploadDtos.ProgressResponse progress(Long uploadId);

    UploadDtos.MergeResponse merge(Long uploadId, UploadDtos.MergeRequest request, String idempotencyKey);

    void abort(Long uploadId);
}
