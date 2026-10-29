package com.campus.contest.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 本地磁盘实现：文件写入 storage.root 目录，按 UUID 命名，返回相对路径。
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorage implements StorageService {

    @Value("${storage.root}")
    private String root;

    private Path base() {
        return Paths.get(root).toAbsolutePath().normalize();
    }

    /** 解析相对路径并防止路径穿越 */
    private Path resolve(String relativePath) {
        Path base = base();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法路径");
        }
        return target;
    }

    @Override
    public int type() {
        return 1;
    }

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
        Path target = resolve(relative);
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
        Path target = resolve(relativePath);
        if (!Files.exists(target)) {
            return null;
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            throw new RuntimeException("删除文件失败", e);
        }
    }

    @Override
    public void write(String relativePath, InputStream in) {
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败", e);
        }
    }

    @Override
    public InputStream open(String relativePath) {
        Path target = resolve(relativePath);
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("文件不存在：" + relativePath);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }
    }

    @Override
    public OutputStream create(String relativePath) {
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            return Files.newOutputStream(target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException("创建文件失败", e);
        }
    }
}
