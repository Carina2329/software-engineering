package com.campus.contest.module.work;

import com.campus.contest.module.work.dto.FileUploadResult;
import com.campus.contest.module.work.dto.UploadDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件模块外部 JSON 字段契约：统一使用 snake_case。
 */
class WorkDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("上传初始化响应使用 OpenAPI 字段名")
    void initResponseUsesSnakeCase() {
        UploadDtos.InitResponse response = new UploadDtos.InitResponse();
        response.setUploadId("1");
        response.setTotalChunks(2);
        response.setChunkSize(1024);
        response.setInstant(false);
        response.setFileId("9");

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("upload_id").asText()).isEqualTo("1");
        assertThat(json.path("total_chunks").asInt()).isEqualTo(2);
        assertThat(json.path("chunk_size").asInt()).isEqualTo(1024);
        assertThat(json.path("already_exists").asBoolean()).isFalse();
        assertThat(json.path("file_id").asText()).isEqualTo("9");
    }

    @Test
    @DisplayName("上传进度使用字符串状态和缺失分片字段")
    void progressResponseUsesContractFields() {
        UploadDtos.ProgressResponse response = new UploadDtos.ProgressResponse();
        response.setUploadId("1");
        response.setStatus("UPLOADING");
        response.setTotalChunks(3);
        response.setUploadedChunks(1);
        response.setMissingChunks(List.of(1, 2));
        response.setExpiresAt(Instant.parse("2026-11-08T12:00:00Z"));

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("id").asText()).isEqualTo("1");
        assertThat(json.path("status").asText()).isEqualTo("UPLOADING");
        assertThat(json.path("missing_chunks").get(0).asInt()).isEqualTo(1);
        assertThat(json.path("expire_at").asText()).isEqualTo("2026-11-08T12:00:00Z");
    }

    @Test
    @DisplayName("批量上传单个结果使用 snake_case 文件字段")
    void fileUploadResultUsesSnakeCase() {
        FileUploadResult result = new FileUploadResult();
        result.setFileName("demo.pdf");
        result.setFileId("9");
        result.setSuccess(true);

        JsonNode json = objectMapper.valueToTree(result);

        assertThat(json.path("file_name").asText()).isEqualTo("demo.pdf");
        assertThat(json.path("file_id").asText()).isEqualTo("9");
    }
}
