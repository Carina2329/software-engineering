package com.campus.contest.module.system.controller;

import com.campus.contest.common.Result;
import com.campus.contest.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;

/**
 * 存储连通性测试接口：上传一个文件、下载回来。正式开发后可删除。
 */
@RestController
@RequestMapping("/api/v1/test/storage")
public class TestStorageController {

    private final StorageService storage;

    public TestStorageController(StorageService storage) {
        this.storage = storage;
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(storage.store(file));
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("path") String relativePath) {
        Resource resource = storage.load(relativePath);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = Paths.get(relativePath).getFileName().toString();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
