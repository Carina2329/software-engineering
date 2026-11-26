package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegistrationAttachmentVO(
        @JsonProperty("file_id") String fileId,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_type") String fileType,
        @JsonProperty("sort_no") int sortNo
) {
}
