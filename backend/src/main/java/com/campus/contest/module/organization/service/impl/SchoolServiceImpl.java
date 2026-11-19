package com.campus.contest.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.organization.dto.SchoolPageQuery;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.service.SchoolService;
import com.campus.contest.module.organization.vo.SchoolItemVO;
import com.campus.contest.module.organization.vo.SchoolPageVO;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.module.system.service.DictionaryQueryService.DictionaryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int ACTIVE = 1;
    private static final String SCHOOL_TYPE = "SCHOOL";
    private static final String PROVINCE_DICTIONARY = "CN_PROVINCE";

    private final OrganizationMapper organizationMapper;
    private final DictionaryQueryService dictionaryQueryService;

    @Override
    public SchoolPageVO pageSchools(SchoolPageQuery query) {
        SchoolPageQuery condition = query == null ? new SchoolPageQuery() : query;
        int page = condition.getPage() == null ? DEFAULT_PAGE : condition.getPage();
        int size = condition.getSize() == null ? DEFAULT_SIZE : condition.getSize();
        if (page < 1) {
            throw invalidParam("page 必须大于等于 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw invalidParam("size 必须为 1-" + MAX_SIZE);
        }

        List<DictionaryItem> provinceItems =
                dictionaryQueryService.listEnabledItems(PROVINCE_DICTIONARY);
        Map<String, String> provinceNames = new LinkedHashMap<>();
        for (DictionaryItem item : provinceItems) {
            provinceNames.put(item.code(), item.name());
        }
        if (provinceNames.isEmpty()) {
            return SchoolPageVO.empty(page, size);
        }

        String provinceCode = null;
        if (StringUtils.hasText(condition.getProvinceCode())) {
            provinceCode = condition.getProvinceCode().trim();
            if (!provinceNames.containsKey(provinceCode)) {
                throw invalidParam("province_code 不在启用的 CN_PROVINCE 字典中");
            }
        }

        LambdaQueryWrapper<OrganizationDO> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(OrganizationDO::getOrgType, SCHOOL_TYPE)
                .eq(OrganizationDO::getStatus, ACTIVE)
                .in(OrganizationDO::getProvinceCode, provinceNames.keySet());
        if (provinceCode != null) {
            wrapper.eq(OrganizationDO::getProvinceCode, provinceCode);
        }
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(nested -> nested
                    .like(OrganizationDO::getOrgCode, keyword)
                    .or()
                    .like(OrganizationDO::getOrgName, keyword));
        }
        wrapper.orderByAsc(OrganizationDO::getOrgName)
                .orderByAsc(OrganizationDO::getId);

        IPage<OrganizationDO> paged = organizationMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );
        List<SchoolItemVO> items = paged.getRecords().stream()
                .map(school -> SchoolItemVO.from(
                        school,
                        provinceNames.get(school.getProvinceCode())
                ))
                .toList();
        long total = paged.getTotal();
        return new SchoolPageVO(
                items,
                total,
                page,
                size,
                (long) page * size < total
        );
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }
}
