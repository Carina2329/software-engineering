package com.campus.contest.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 存储服务：文件存取的统一抽象。
 * 本地磁盘与对象存储（SeaweedFS / OSS）分别实现，业务代码只依赖本接口。
 */
public interface StorageService {

    /** 保存文件，返回相对于存储根目录的相对路径 */
    String store(MultipartFile file);

    /** 读取文件；不存在返回 null */
    Resource load(String relativePath);

    /** 删除文件 */
    void delete(String relativePath);
}
