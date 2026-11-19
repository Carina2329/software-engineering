package com.campus.contest.module.organization;

import com.campus.contest.module.organization.controller.SchoolController;
import com.campus.contest.module.organization.dto.SchoolPageQuery;
import com.campus.contest.module.organization.service.SchoolService;
import com.campus.contest.module.organization.vo.SchoolItemVO;
import com.campus.contest.module.organization.vo.SchoolPageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolControllerTest {

    @Mock
    private SchoolService schoolService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SchoolController(schoolService))
                .build();
    }

    @Test
    void exposesSchoolListAndSnakeCaseFields() throws Exception {
        SchoolItemVO item = new SchoolItemVO();
        item.setId("10");
        item.setSchoolCode("SC-A");
        item.setSchoolName("四川A大学");
        item.setProvinceCode("510000");
        item.setProvinceName("四川省");
        when(schoolService.pageSchools(any())).thenReturn(
                new SchoolPageVO(List.of(item), 1L, 1, 20, false)
        );

        mockMvc.perform(get("/api/v1/schools")
                        .queryParam("page", "1")
                        .queryParam("size", "20")
                        .queryParam("province_code", "510000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].school_code").value("SC-A"))
                .andExpect(jsonPath("$.data.items[0].province_name").value("四川省"));

        ArgumentCaptor<SchoolPageQuery> captor = ArgumentCaptor.forClass(SchoolPageQuery.class);
        verify(schoolService).pageSchools(captor.capture());
        assertThat(captor.getValue().getProvinceCode()).isEqualTo("510000");
    }
}
