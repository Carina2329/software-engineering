package com.campus.contest.module.work.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.work.dto.UploadDtos;
import com.campus.contest.module.work.service.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 大文件分片上传：EP-046 ~ EP-049，以及放弃上传（清理分片）。
 *
 * 四个接口在 OpenAPI 中的 x-permission 都是 file:upload，因此统一加方法级鉴权；
 * EP-046 初始化与 EP-049 合并的 Idempotency-Key 在契约中标记为必填，这里只负责接收，
 * 必填校验放在 Service 层，缺 key 返回 400 + 10001。
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** EP-046 初始化大文件分片上传 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('file:upload')")
    public Result<UploadDtos.InitResponse> init(
            @RequestBody UploadDtos.InitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.ok(uploadService.init(request, idempotencyKey));
    }

    /** EP-047 上传单个分片 */
    @PutMapping("/{uploadId}/chunks/{chunkIndex}")
    @PreAuthorize("hasAuthority('file:upload')")
    public Result<UploadDtos.ChunkResponse> uploadChunk(
            @PathVariable Long uploadId,
            @PathVariable Integer chunkIndex,
            @RequestHeader("X-Chunk-SHA256") String expectedSha256,
            @RequestBody byte[] body) {
        return Result.ok(uploadService.uploadChunk(uploadId, chunkIndex, expectedSha256, body));
    }

    /** EP-048 查询上传进度 */
    @GetMapping("/{uploadId}")
    @PreAuthorize("hasAuthority('file:upload')")
    public Result<UploadDtos.ProgressResponse> progress(@PathVariable Long uploadId) {
        return Result.ok(uploadService.progress(uploadId));
    }

    /** EP-049 后台合并分片 */
    @PostMapping("/{uploadId}/merge")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('file:upload')")
    public Result<UploadDtos.MergeResponse> merge(
            @PathVariable Long uploadId,
            @RequestBody(required = false) UploadDtos.MergeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.ok(uploadService.merge(uploadId, request, idempotencyKey));
    }

    /** 放弃上传并清理分片，同样属于上传能力，沿用 file:upload */
    @DeleteMapping("/{uploadId}")
    @PreAuthorize("hasAuthority('file:upload')")
    public Result<Void> abort(@PathVariable Long uploadId) {
        uploadService.abort(uploadId);
        return Result.ok();
    }
}
