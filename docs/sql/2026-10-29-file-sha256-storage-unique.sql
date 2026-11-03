-- 同一文件允许在本地磁盘和对象存储各保存一份，但同一存储后端仍只保留一份。
ALTER TABLE `file`
    DROP INDEX `uk_file_sha256`,
    ADD UNIQUE KEY `uk_file_sha256_storage` (`sha256`, `storage_type`);
