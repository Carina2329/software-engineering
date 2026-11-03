package com.campus.contest.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * SeaweedFS 实现：通过本地 S3 兼容接口保存对象。
 * 对象存储没有本地文件那种可长期打开的 OutputStream，合并大文件时先用临时文件承接，关闭流后一次上传。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "seaweedfs")
public class SeaweedStorage implements StorageService {

    @Value("${storage.seaweedfs.endpoint:http://127.0.0.1:8333}")
    private String endpoint;

    @Value("${storage.seaweedfs.bucket:contest-files}")
    private String bucket;

    @Value("${storage.seaweedfs.region:us-east-1}")
    private String region;

    @Value("${storage.seaweedfs.access-key:}")
    private String accessKey;

    @Value("${storage.seaweedfs.secret-key:}")
    private String secretKey;

    @Value("${storage.seaweedfs.temp-dir:D:/codex/storage/seaweed-temp}")
    private String tempDir;

    private S3Client s3;

    @PostConstruct
    void init() {
        var builder = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder());

        if (StringUtils.hasText(accessKey) || StringUtils.hasText(secretKey)) {
            if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
                throw new IllegalStateException("SeaweedFS access-key 和 secret-key 必须同时配置");
            }
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(AnonymousCredentialsProvider.create());
        }

        s3 = builder.build();
        ensureBucket();
        log.info("SeaweedFS 存储已启用：endpoint={}，bucket={}", endpoint, bucket);
    }

    @PreDestroy
    void close() {
        if (s3 != null) {
            s3.close();
        }
    }

    @Override
    public int type() {
        return 2;
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String relativePath = "files/" + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        Path temp = null;
        try {
            Path directory = tempDirectory();
            temp = Files.createTempFile(directory, "upload-", ".part");
            file.transferTo(temp);
            putObject(relativePath, temp, file.getContentType());
            return relativePath;
        } catch (IOException e) {
            throw new RuntimeException("保存上传临时文件失败", e);
        } finally {
            deleteTemp(temp);
        }
    }

    @Override
    public Resource load(String relativePath) {
        String key = normalizeKey(relativePath);
        try {
            ResponseInputStream<GetObjectResponse> in = s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return new InputStreamResource(in) {
                @Override
                public long contentLength() {
                    return in.response().contentLength();
                }

                @Override
                public String getFilename() {
                    return Paths.get(key).getFileName().toString();
                }
            };
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw storageError("读取文件", e);
        }
    }

    @Override
    public void delete(String relativePath) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeKey(relativePath))
                    .build());
        } catch (S3Exception e) {
            throw storageError("删除文件", e);
        }
    }

    @Override
    public void write(String relativePath, InputStream in) {
        try {
            byte[] bytes = in.readAllBytes();
            putObject(relativePath, bytes, null);
        } catch (IOException e) {
            throw new RuntimeException("读取待上传内容失败", e);
        }
    }

    @Override
    public InputStream open(String relativePath) {
        try {
            return s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeKey(relativePath))
                    .build());
        } catch (NoSuchKeyException e) {
            throw new IllegalArgumentException("文件不存在：" + relativePath);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException("文件不存在：" + relativePath);
            }
            throw storageError("读取文件", e);
        }
    }

    @Override
    public OutputStream create(String relativePath) {
        String key = normalizeKey(relativePath);
        try {
            Path directory = Paths.get(tempDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "merge-", ".part");
            OutputStream fileOut = Files.newOutputStream(temp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return new UploadOnCloseOutputStream(fileOut, temp, key);
        } catch (IOException e) {
            throw new RuntimeException("创建临时合并文件失败", e);
        }
    }

    private void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                createBucket();
            } else {
                throw storageError("检查存储桶", e);
            }
        }
    }

    private void createBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("已创建 SeaweedFS 存储桶：{}", bucket);
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // 并发初始化时可能已经创建
        } catch (S3Exception e) {
            throw storageError("创建存储桶", e);
        }
    }

    private void putObject(String relativePath, Path source, String contentType) {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(normalizeKey(relativePath));
        if (StringUtils.hasText(contentType)) {
            request.contentType(contentType);
        }
        s3.putObject(request.build(), RequestBody.fromFile(source));
    }

    private void putObject(String relativePath, byte[] bytes, String contentType) {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(normalizeKey(relativePath));
        if (StringUtils.hasText(contentType)) {
            request.contentType(contentType);
        }
        s3.putObject(request.build(), RequestBody.fromBytes(bytes));
    }

    private String normalizeKey(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("存储路径不能为空");
        }
        String key = relativePath.replace('\\', '/');
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.isEmpty() || key.contains("../") || key.equals("..")) {
            throw new IllegalArgumentException("非法存储路径");
        }
        return key;
    }

    private String extensionOf(String fileName) {
        if (fileName != null && fileName.lastIndexOf('.') >= 0) {
            return fileName.substring(fileName.lastIndexOf('.'));
        }
        return "";
    }

    private Path tempDirectory() throws IOException {
        Path directory = Paths.get(tempDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        return directory;
    }

    private void deleteTemp(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.warn("删除 SeaweedFS 临时文件失败：{}", temp, e);
        }
    }

    private RuntimeException storageError(String action, S3Exception e) {
        log.error("SeaweedFS {}失败：status={}，message={}", action, e.statusCode(), e.getMessage());
        return new RuntimeException("SeaweedFS " + action + "失败：" + e.getMessage(), e);
    }

    private final class UploadOnCloseOutputStream extends FilterOutputStream {

        private final Path temp;
        private final String key;
        private boolean closed;

        private UploadOnCloseOutputStream(OutputStream out, Path temp, String key) {
            super(out);
            this.temp = temp;
            this.key = key;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                out.close();
                putObject(key, temp, null);
            } catch (IOException | RuntimeException e) {
                throw new IOException("上传合并文件到 SeaweedFS 失败", e);
            } finally {
                deleteTemp(temp);
            }
        }
    }
}
