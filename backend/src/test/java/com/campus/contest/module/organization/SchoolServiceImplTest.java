package com.campus.contest.module.organization;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.organization.dto.SchoolPageQuery;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.service.impl.SchoolServiceImpl;
import com.campus.contest.module.organization.vo.SchoolPageVO;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.module.system.service.DictionaryQueryService.DictionaryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolServiceImplTest {

    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private DictionaryQueryService dictionaryQueryService;

    private SchoolServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolServiceImpl(organizationMapper, dictionaryQueryService);
    }

    @Test
    @DisplayName("EP-085 返回启用学校的省份名称")
    void listsEnabledSchoolsWithProvinceName() {
        when(dictionaryQueryService.listEnabledItems("CN_PROVINCE")).thenReturn(List.of(
                new DictionaryItem("1", "510000", "四川省")
        ));
        Page<OrganizationDO> page = new Page<>(1, 20);
        page.setRecords(List.of(
                school(10L, "SC-A", "四川A大学", "510000", 1)
        ));
        page.setTotal(1);
        when(organizationMapper.selectPage(any(Page.class), any())).thenReturn(page);

        SchoolPageVO result = service.pageSchools(new SchoolPageQuery());

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo("10");
            assertThat(item.getSchoolCode()).isEqualTo("SC-A");
            assertThat(item.getProvinceName()).isEqualTo("四川省");
        });
    }

    @Test
    @DisplayName("非法省份筛选返回 400 且不查询数据库")
    void rejectsUnknownProvinceFilter() {
        when(dictionaryQueryService.listEnabledItems("CN_PROVINCE")).thenReturn(List.of(
                new DictionaryItem("1", "510000", "四川省")
        ));
        SchoolPageQuery query = new SchoolPageQuery();
        query.setProvinceCode("999999");

        assertThatThrownBy(() -> service.pageSchools(query))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(organizationMapper, never()).selectPage(any(), any());
    }

    private OrganizationDO school(
            Long id,
            String code,
            String name,
            String provinceCode,
            int status
    ) {
        OrganizationDO school = new OrganizationDO();
        school.setId(id);
        school.setOrgCode(code);
        school.setOrgName(name);
        school.setOrgType("SCHOOL");
        school.setProvinceCode(provinceCode);
        school.setStatus(status);
        school.setDeleted(0);
        return school;
    }
}
