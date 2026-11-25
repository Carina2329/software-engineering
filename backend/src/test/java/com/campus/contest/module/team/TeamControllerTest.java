package com.campus.contest.module.team;

import com.campus.contest.module.team.controller.TeamController;
import com.campus.contest.module.team.dto.TeamApprovalRequest;
import com.campus.contest.module.team.dto.TeamPageQuery;
import com.campus.contest.module.team.service.TeamService;
import com.campus.contest.module.team.vo.TeamApprovalVO;
import com.campus.contest.module.team.vo.TeamPageVO;
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
class TeamControllerTest {

    @Mock
    private TeamService teamService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TeamController(teamService))
                .build();
    }

    @Test
    void bindsSnakeCaseTopicAndTeacherQueryParameters() throws Exception {
        when(teamService.list(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TeamPageVO(List.of(), 0, 1, 20, false));

        mockMvc.perform(get("/api/v1/editions/1/teams")
                        .queryParam("page", "1")
                        .queryParam("size", "20")
                        .queryParam("topic_id", "31")
                        .queryParam("teacher_id", "8")
                        .queryParam("status", "DRAFT"))
                .andExpect(status().isOk());

        ArgumentCaptor<TeamPageQuery> captor = ArgumentCaptor.forClass(TeamPageQuery.class);
        verify(teamService).list(eq(1L), captor.capture());
        assertThat(captor.getValue().getTopicId()).isEqualTo("31");
        assertThat(captor.getValue().getTeacherId()).isEqualTo("8");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
    }
    @Test
    void exposesApprovalEndpointWithIdempotencyHeader() throws Exception {
        when(teamService.approveRegistration(eq(100L), org.mockito.ArgumentMatchers.any(), eq("approve-key")))
                .thenReturn(new TeamApprovalVO("100", "APPROVED", 3, "700"));

        mockMvc.perform(post("/api/v1/teams/100/approval")
                        .header("Idempotency-Key", "approve-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "ADMIN",
                                  "decision": "APPROVE",
                                  "version": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team_id").value("100"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.version").value(3));

        ArgumentCaptor<TeamApprovalRequest> captor =
                ArgumentCaptor.forClass(TeamApprovalRequest.class);
        verify(teamService).approveRegistration(eq(100L), captor.capture(), eq("approve-key"));
        assertThat(captor.getValue().getStage()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
    }
}
