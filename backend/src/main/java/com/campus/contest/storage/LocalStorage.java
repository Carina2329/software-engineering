package com.campus.contest.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地磁盘实现：文件写入 storage.root 目录，按 UUID 命名，返回相对路径。
 */
@Service
public class LocalStorage implements StorageService {

    @Value("${storage.root}")
    private String root;

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.lastIndexOf('.') >= 0) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String relative = "files/" + UUID.randomUUID() + ext;

        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法路径");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败", e);
        }
        return relative;
    }

    @Override
    public Resource load(String relativePath) {
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base) || !Files.exists(target)) {
            return null;
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(Paths.get(root).toAbsolutePath().resolve(relativePath));
        } catch (IOException e) {
            throw new RuntimeException("删除文件失败", e);
        }
    }
}
