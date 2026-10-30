package com.campus.contest.module.work.service;

import com.campus.contest.module.work.dto.UploadDtos;

public interface UploadService {

    UploadDtos.InitResponse init(UploadDtos.InitRequest request);

    UploadDtos.ChunkResponse uploadChunk(Long uploadId, Integer chunkIndex, String expectedSha256, byte[] body);

    UploadDtos.ProgressResponse progress(Long uploadId);

    UploadDtos.MergeResponse merge(Long uploadId, UploadDtos.MergeRequest request);

    void abort(Long uploadId);
}
