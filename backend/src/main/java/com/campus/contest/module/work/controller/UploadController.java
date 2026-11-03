package com.campus.contest.module.work.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.work.dto.UploadDtos;
import com.campus.contest.module.work.service.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 大文件分片上传：EP-046 ~ EP-049，以及放弃上传（清理分片）。
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UploadDtos.InitResponse> init(@RequestBody UploadDtos.InitRequest request) {
        return Result.ok(uploadService.init(request));
    }

    @PutMapping("/{uploadId}/chunks/{chunkIndex}")
    public Result<UploadDtos.ChunkResponse> uploadChunk(
            @PathVariable Long uploadId,
            @PathVariable Integer chunkIndex,
            @RequestHeader(value = "X-Chunk-SHA256", required = false) String expectedSha256,
            @RequestBody byte[] body) {
        return Result.ok(uploadService.uploadChunk(uploadId, chunkIndex, expectedSha256, body));
    }

    @GetMapping("/{uploadId}")
    public Result<UploadDtos.ProgressResponse> progress(@PathVariable Long uploadId) {
        return Result.ok(uploadService.progress(uploadId));
    }

    @PostMapping("/{uploadId}/merge")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<UploadDtos.MergeResponse> merge(
            @PathVariable Long uploadId,
            @RequestBody(required = false) UploadDtos.MergeRequest request) {
        return Result.ok(uploadService.merge(uploadId, request));
    }

    @DeleteMapping("/{uploadId}")
    public Result<Void> abort(@PathVariable Long uploadId) {
        uploadService.abort(uploadId);
        return Result.ok();
    }
}
