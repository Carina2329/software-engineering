package com.campus.contest.module.team;

import com.campus.contest.module.team.controller.EditionEntryGrantController;
import com.campus.contest.module.team.dto.EntryGrantBatchRequest;
import com.campus.contest.module.team.service.EditionEntryGrantService;
import com.campus.contest.module.team.vo.EntryGrantBatchVO;
import com.campus.contest.module.team.vo.EntryGrantListVO;
import com.campus.contest.module.team.vo.EntryGrantVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EditionEntryGrantControllerTest {

    @Mock
    private EditionEntryGrantService entryGrantService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EditionEntryGrantController(entryGrantService))
                .build();
    }

    @Test
    void exposesBatchEndpointWithSnakeCaseContractAndIdempotencyHeader() throws Exception {
        when(entryGrantService.batch(
                eq(10L),
                org.mockito.ArgumentMatchers.any(),
                eq("grant-key")
        )).thenReturn(new EntryGrantBatchVO(1, 0));

        mockMvc.perform(post("/api/v1/editions/10/entry-grants/batch")
                        .header("Idempotency-Key", "grant-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [{
                                    "action": "UPSERT",
                                    "grant_type": "QUALIFIED",
                                    "leader_name": "张三",
                                    "school_id": "20",
                                    "student_no": "S001"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upserted_count").value(1))
                .andExpect(jsonPath("$.data.revoked_count").value(0));

        ArgumentCaptor<EntryGrantBatchRequest> captor =
                ArgumentCaptor.forClass(EntryGrantBatchRequest.class);
        verify(entryGrantService).batch(eq(10L), captor.capture(), eq("grant-key"));
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().get(0).getGrantType()).isEqualTo("QUALIFIED");
        assertThat(captor.getValue().getItems().get(0).getStudentNo()).isEqualTo("S001");
    }

    @Test
    void exposesStatusFilterAndContractFieldNames() throws Exception {
        when(entryGrantService.list(10L, "REVOKED")).thenReturn(new EntryGrantListVO(List.of(
                new EntryGrantVO(
                        "55",
                        "INVITED",
                        null,
                        "张三",
                        "20",
                        "S001",
                        null,
                        null,
                        "100",
                        "REVOKED",
                        "撤销",
                        2
                )
        )));

        mockMvc.perform(get("/api/v1/editions/10/entry-grants")
                        .queryParam("status", "REVOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("55"))
                .andExpect(jsonPath("$.data.items[0].target_team_id").value("100"))
                .andExpect(jsonPath("$.data.items[0].status").value("REVOKED"))
                .andExpect(jsonPath("$.data.items[0].version").value(2));

        verify(entryGrantService).list(10L, "REVOKED");
    }
}
