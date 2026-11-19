package com.campus.contest.module.organization;

import com.campus.contest.module.organization.controller.OrganizationController;
import com.campus.contest.module.organization.dto.OrganizationPageQuery;
import com.campus.contest.module.organization.dto.OrganizationUpdateRequest;
import com.campus.contest.module.organization.service.OrganizationService;
import com.campus.contest.module.organization.vo.OrganizationCreatedVO;
import com.campus.contest.module.organization.vo.OrganizationItemVO;
import com.campus.contest.module.organization.vo.OrganizationPageVO;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    @Mock
    private OrganizationService organizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrganizationController(organizationService))
                .build();
    }

    @Test
    void exposesOrganizationListAndSnakeCaseQuery() throws Exception {
        OrganizationItemVO item = new OrganizationItemVO();
        item.setId("10");
        item.setOrgCode("SC-A");
        item.setOrgName("四川A大学");
        item.setOrgType("SCHOOL");
        item.setProvinceCode("510000");
        item.setStatus(1);
        item.setVersion(0);
        when(organizationService.pageOrganizations(any())).thenReturn(
                new OrganizationPageVO(List.of(item), 1L, 2, 20, false)
        );

        mockMvc.perform(get("/api/v1/organizations")
                        .queryParam("page", "2")
                        .queryParam("size", "20")
                        .queryParam("org_type", "SCHOOL")
                        .queryParam("province_code", "510000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].org_code").value("SC-A"))
                .andExpect(jsonPath("$.data.has_more").value(false));

        ArgumentCaptor<OrganizationPageQuery> captor =
                ArgumentCaptor.forClass(OrganizationPageQuery.class);
        verify(organizationService).pageOrganizations(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getOrgType()).isEqualTo("SCHOOL");
        assertThat(captor.getValue().getProvinceCode()).isEqualTo("510000");
    }

    @Test
    void createsOrganizationWithIdempotencyHeaderAnd201() throws Exception {
        when(organizationService.createOrganization(any(), eq("create-key")))
                .thenReturn(new OrganizationCreatedVO("10", "SC-A"));

        mockMvc.perform(post("/api/v1/organizations")
                        .header("Idempotency-Key", "create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "org_code": "SC-A",
                                  "org_name": "四川A大学",
                                  "org_type": "SCHOOL",
                                  "province_code": "510000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("10"))
                .andExpect(jsonPath("$.data.org_code").value("SC-A"));
    }

    @Test
    void exposesDetailAndPatchWithoutPathDrift() throws Exception {
        OrganizationItemVO item = new OrganizationItemVO();
        item.setId("10");
        when(organizationService.getOrganization(10L)).thenReturn(item);
        when(organizationService.updateOrganization(eq(10L), any(), eq("update-key")))
                .thenReturn(new IdVersionVO("10", 2));

        mockMvc.perform(get("/api/v1/organizations/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("10"));

        mockMvc.perform(patch("/api/v1/organizations/10")
                        .header("Idempotency-Key", "update-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": 0, "version": 1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        ArgumentCaptor<OrganizationUpdateRequest> captor =
                ArgumentCaptor.forClass(OrganizationUpdateRequest.class);
        verify(organizationService).updateOrganization(eq(10L), captor.capture(), eq("update-key"));
        assertThat(captor.getValue().getStatus()).isZero();
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
    }
}
