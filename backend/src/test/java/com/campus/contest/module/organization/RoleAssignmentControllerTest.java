package com.campus.contest.module.organization;

import com.campus.contest.module.organization.controller.RoleAssignmentController;
import com.campus.contest.module.organization.dto.RoleAssignmentUpdateRequest;
import com.campus.contest.module.organization.service.RoleAssignmentService;
import com.campus.contest.module.system.vo.IdVersionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentControllerTest {

    @Mock
    private RoleAssignmentService roleAssignmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoleAssignmentController(roleAssignmentService))
                .build();
    }

    @Test
    void createsAssignmentWith201AndIdempotencyHeader() throws Exception {
        when(roleAssignmentService.createAssignment(any(), eq("create-key")))
                .thenReturn(new IdVersionVO("100", 0));

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Idempotency-Key", "create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "5",
                                  "role_code": "EVENT_MANAGER",
                                  "scope_type": "ORGANIZATION",
                                  "scope_id": "10"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("100"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void updatesAssignmentWithoutPathDrift() throws Exception {
        when(roleAssignmentService.updateAssignment(eq(100L), any(), eq("update-key")))
                .thenReturn(new IdVersionVO("100", 5));

        mockMvc.perform(patch("/api/v1/role-assignments/100")
                        .header("Idempotency-Key", "update-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false, "version": 4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(5));

        ArgumentCaptor<RoleAssignmentUpdateRequest> captor =
                ArgumentCaptor.forClass(RoleAssignmentUpdateRequest.class);
        verify(roleAssignmentService).updateAssignment(
                eq(100L),
                captor.capture(),
                eq("update-key")
        );
        assertThat(captor.getValue().getActive()).isFalse();
        assertThat(captor.getValue().getVersion()).isEqualTo(4);
    }
}
