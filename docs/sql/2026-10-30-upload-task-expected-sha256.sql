-- 保存客户端提供的整文件 SHA-256，合并后由服务端再次校验。
ALTER TABLE `upload_task`
    ADD COLUMN `expected_sha256` char(64) DEFAULT NULL COMMENT '客户端完整文件SHA-256' AFTER `uploaded_chunks`;
