package com.campus.contest.module.work.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.work.dto.BatchUploadResponse;
import com.campus.contest.module.work.dto.FileUploadResult;
import com.campus.contest.module.work.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件接口：EP-045 小文件批量上传。
 * 后续 EP-046~051（分片上传、断点续传、下载）继续加在本类。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<BatchUploadResponse> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "purpose", required = false) String purpose) {
        List<FileUploadResult> results = fileService.batchUpload(files, purpose);
        BatchUploadResponse response = new BatchUploadResponse();
        response.setResults(results);
        return Result.ok(response);
    }
}
