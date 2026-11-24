package com.campus.contest.module.work.model;

public record ValidatedRegistrationAttachment(
        Long fileId,
        String originalName,
        String mimeType,
        long fileSize,
        String materialType
) {
}
