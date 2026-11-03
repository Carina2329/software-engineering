package com.campus.contest.module.work.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.contest.module.work.dto.FileUploadResult;
import com.campus.contest.module.work.entity.FileDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService extends IService<FileDO> {

    /**
     * 小文件批量上传：逐个存盘、写库，单文件失败不影响其他文件。
     */
    List<FileUploadResult> batchUpload(List<MultipartFile> files, String purpose);
}
