package com.campus.contest.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 存储服务：文件存取的统一抽象。
 * 本地磁盘与对象存储（SeaweedFS / OSS）分别实现，业务代码只依赖本接口。
 */
public interface StorageService {

    /** 文件存储类型：1 本地磁盘，2 对象存储 */
    int type();

    /** 保存上传文件，返回相对于存储根目录的相对路径 */
    String store(MultipartFile file);

    /** 读取文件；不存在返回 null */
    Resource load(String relativePath);

    /** 删除文件 */
    void delete(String relativePath);

    /** 把输入流写入指定相对路径（分片上传用） */
    void write(String relativePath, InputStream in);

    /** 打开指定相对路径的输入流（合并分片用） */
    InputStream open(String relativePath);

    /** 创建指定相对路径的输出流（合并分片用） */
    OutputStream create(String relativePath);
}
