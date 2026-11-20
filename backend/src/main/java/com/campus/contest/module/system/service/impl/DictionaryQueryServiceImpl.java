package com.campus.contest.module.system.service.impl;

import com.campus.contest.module.system.entity.SysDictItemDO;
import com.campus.contest.module.system.entity.SysDictTypeDO;
import com.campus.contest.module.system.mapper.SysDictItemMapper;
import com.campus.contest.module.system.mapper.SysDictTypeMapper;
import com.campus.contest.module.system.service.DictionaryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DictionaryQueryServiceImpl implements DictionaryQueryService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;

    @Override
    public List<DictionaryItem> listEnabledItems(String dictCode) {
        SysDictTypeDO type = enabledType(dictCode);
        if (type == null) {
            return List.of();
        }
        return enabledItems(type.getId()).stream()
                .map(this::toItem)
                .toList();
    }

    @Override
    public boolean containsEnabledValue(String dictCode, String itemValue) {
        return findEnabledItem(dictCode, itemValue).isPresent();
    }

    @Override
    public Optional<String> findEnabledName(String dictCode, String itemValue) {
        return findEnabledItem(dictCode, itemValue)
                .map(SysDictItemDO::getItemLabel);
    }

    private Optional<SysDictItemDO> findEnabledItem(String dictCode, String itemValue) {
        String normalizedValue = normalize(itemValue);
        if (normalizedValue == null) {
            return Optional.empty();
        }
        SysDictTypeDO type = enabledType(dictCode);
        if (type == null) {
            return Optional.empty();
        }
        return enabledItems(type.getId()).stream()
                .filter(item -> normalizedValue.equals(item.getItemValue()))
                .findFirst();
    }

    private SysDictTypeDO enabledType(String dictCode) {
        String normalizedCode = normalize(dictCode);
        return normalizedCode == null ? null : dictTypeMapper.selectEnabledByCode(normalizedCode);
    }

    private List<SysDictItemDO> enabledItems(Long dictTypeId) {
        List<SysDictItemDO> items = dictItemMapper.selectEnabledByTypeId(dictTypeId);
        return items == null ? List.of() : items;
    }

    private DictionaryItem toItem(SysDictItemDO item) {
        return new DictionaryItem(
                item.getId() == null ? null : String.valueOf(item.getId()),
                item.getItemValue(),
                item.getItemLabel()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
