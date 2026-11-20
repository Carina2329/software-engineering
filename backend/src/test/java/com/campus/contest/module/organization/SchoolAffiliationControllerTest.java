package com.campus.contest.module.organization;

import com.campus.contest.module.organization.controller.SchoolAffiliationController;
import com.campus.contest.module.organization.dto.SchoolAffiliationPageQuery;
import com.campus.contest.module.organization.dto.SchoolAffiliationReviewRequest;
import com.campus.contest.module.organization.service.SchoolAffiliationService;
import com.campus.contest.module.organization.vo.SchoolAffiliationItemVO;
import com.campus.contest.module.organization.vo.SchoolAffiliationPageVO;
import com.campus.contest.module.organization.vo.SchoolAffiliationReviewVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolAffiliationControllerTest {

    @Mock
    private SchoolAffiliationService schoolAffiliationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SchoolAffiliationController(schoolAffiliationService))
                .build();
    }

    @Test
    void exposesAffiliationListAndSnakeCaseQuery() throws Exception {
        SchoolAffiliationItemVO item = new SchoolAffiliationItemVO();
        item.setUserId("5");
        item.setSchoolId("10");
        item.setSchoolName("四川A大学");
        item.setStatus("PENDING");
        when(schoolAffiliationService.pageAffiliations(any())).thenReturn(
                new SchoolAffiliationPageVO(List.of(item), 1L, 1, 20, false)
        );

        mockMvc.perform(get("/api/v1/school-affiliations")
                        .queryParam("status", "PENDING")
                        .queryParam("school_id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].user_id").value("5"))
                .andExpect(jsonPath("$.data.items[0].school_name").value("四川A大学"));

        ArgumentCaptor<SchoolAffiliationPageQuery> captor =
                ArgumentCaptor.forClass(SchoolAffiliationPageQuery.class);
        verify(schoolAffiliationService).pageAffiliations(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo("10");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void exposesReviewWithOptionalIdempotencyHeader() throws Exception {
        when(schoolAffiliationService.reviewAffiliation(
                eq(5L),
                any(),
                eq("review-key")
        )).thenReturn(new SchoolAffiliationReviewVO("5", "VERIFIED", 3));

        mockMvc.perform(patch("/api/v1/school-affiliations/5")
                        .header("Idempotency-Key", "review-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE", "version": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value("5"))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.version").value(3));

        ArgumentCaptor<SchoolAffiliationReviewRequest> captor =
                ArgumentCaptor.forClass(SchoolAffiliationReviewRequest.class);
        verify(schoolAffiliationService).reviewAffiliation(
                eq(5L),
                captor.capture(),
                eq("review-key")
        );
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
    }
}
