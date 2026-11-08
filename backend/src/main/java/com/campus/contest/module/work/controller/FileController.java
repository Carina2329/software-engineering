package com.campus.contest.module.work.controller;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.Result;
import com.campus.contest.module.work.dto.BatchUploadResponse;
import com.campus.contest.module.work.dto.FileUploadResult;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.service.FileAccessGuard;
import com.campus.contest.module.work.service.FileService;
import com.campus.contest.storage.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件接口：EP-045 小文件批量上传、EP-050 断点续传下载。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;
    private final StorageService storageService;
    private final FileAccessGuard fileAccessGuard;

    public FileController(FileService fileService,
                          StorageService storageService,
                          FileAccessGuard fileAccessGuard) {
        this.fileService = fileService;
        this.storageService = storageService;
        this.fileAccessGuard = fileAccessGuard;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('file:upload')")
    public Result<BatchUploadResponse> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "purpose", required = false) String purpose) {
        List<FileUploadResult> results = fileService.batchUpload(files, purpose);
        BatchUploadResponse response = new BatchUploadResponse();
        response.setResults(results);
        return Result.ok(response);
    }

    /**
     * 断点续传下载：支持 Range 请求，返回 206 与 Content-Range。
     */
    @GetMapping("/{fileId}/content")
    @PreAuthorize("hasAuthority('file:download')")
    public void download(@PathVariable Long fileId,
                         @RequestHeader(value = "Range", required = false) String rangeHeader,
                         HttpServletResponse response) {
        FileDO file = fileService.getById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在或对当前用户不可见");
        }

        // EP-050 资源归属检查：非上传人且非管理员访问他人文件按不可见处理
        fileAccessGuard.requireDownloadable(file);

        long length = file.getFileSize() == null ? 0L : file.getFileSize();
        if (length <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在或对当前用户不可见");
        }

        long start = 0;
        long end = length - 1;
        boolean partial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length()).trim();
            String[] parts = spec.split("-", -1);
            try {
                if (parts[0].isEmpty()) {
                    long suffix = Long.parseLong(parts[1]);
                    start = Math.max(0, length - suffix);
                } else {
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Math.min(Long.parseLong(parts[1]), length - 1);
                    }
                }
            } catch (NumberFormatException e) {
                rangeNotSatisfiable(response, length);
                return;
            }
            if (start < 0 || start > end || start >= length) {
                rangeNotSatisfiable(response, length);
                return;
            }
            partial = true;
        }

        long count = end - start + 1;
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(file.getMimeType() != null ? file.getMimeType() : "application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encode(file.getOriginalName()));
        response.setContentLengthLong(count);

        if (partial) {
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
        } else {
            response.setStatus(HttpStatus.OK.value());
        }

        try (InputStream in = storageService.open(file.getStoragePath())) {
            in.skipNBytes(start);
            byte[] buffer = new byte[8192];
            long remaining = count;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                response.getOutputStream().write(buffer, 0, read);
                remaining -= read;
            }
            response.flushBuffer();
        } catch (Exception e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void rangeNotSatisfiable(HttpServletResponse response, long length) {
        response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
        response.setHeader("Content-Range", "bytes */" + length);
    }

    private String encode(String name) {
        if (name == null) {
            return "download";
        }
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
