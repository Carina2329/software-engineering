# SeaweedFS 文件存储说明

## 启动

在项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-seaweedfs.ps1
```

默认端口：

| 服务 | 地址 |
|---|---|
| S3 API | http://127.0.0.1:8333 |
| Filer | http://127.0.0.1:8888 |
| Master | http://127.0.0.1:9333 |
| Volume | http://127.0.0.1:8082 |

Volume 使用 `8082`，避免占用 Spring Boot 的 `8080`。

## 切换存储

配置文件：`backend/src/main/resources/application.yml`

```yaml
storage:
  type: seaweedfs
```

- `local`：保存到 `D:/codex/storage`
- `seaweedfs`：保存到本地 SeaweedFS 的 `contest-files` 桶

Spring Boot 只会创建当前配置对应的 `StorageService` 实现，上传、分片、合并和下载业务不需要修改。

## 实现说明

`SeaweedStorage` 通过 SeaweedFS 的 S3 兼容接口保存对象。

由于对象存储不支持像本地磁盘一样长期打开 `OutputStream`，大文件合并时先写入 `storage.seaweedfs.temp-dir` 临时文件，关闭输出流后自动上传到 SeaweedFS，再删除临时文件。

## 数据库迁移

执行：

```text
docs/sql/2026-10-29-file-sha256-storage-unique.sql
```

该迁移允许同一个文件在本地存储和对象存储各保存一份，同时仍在单个存储后端内进行 SHA-256 去重。
