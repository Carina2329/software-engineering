package com.campus.contest.module.system.service.impl;

import com.campus.contest.module.system.entity.SysDictItemDO;
import com.campus.contest.module.system.entity.SysDictTypeDO;
import com.campus.contest.module.system.mapper.SysDictItemMapper;
import com.campus.contest.module.system.mapper.SysDictTypeMapper;
import com.campus.contest.module.system.service.DictionaryQueryService.DictionaryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryQueryServiceImplTest {

    @Mock
    private SysDictTypeMapper dictTypeMapper;
    @Mock
    private SysDictItemMapper dictItemMapper;

    private DictionaryQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DictionaryQueryServiceImpl(dictTypeMapper, dictItemMapper);
    }

    @Test
    @DisplayName("按字典编码返回启用项并保留配置顺序")
    void listsEnabledItemsInMapperOrder() {
        when(dictTypeMapper.selectEnabledByCode("CN_PROVINCE")).thenReturn(type(9L, "CN_PROVINCE"));
        when(dictItemMapper.selectEnabledByTypeId(9L)).thenReturn(List.of(
                item(101L, "510000", "四川省"),
                item(102L, "430000", "湖南省")
        ));

        List<DictionaryItem> items = service.listEnabledItems(" CN_PROVINCE ");

        assertThat(items).containsExactly(
                new DictionaryItem("101", "510000", "四川省"),
                new DictionaryItem("102", "430000", "湖南省")
        );
    }

    @Test
    @DisplayName("编码归属和展示名称只查询启用字典")
    void resolvesEnabledValueAndName() {
        when(dictTypeMapper.selectEnabledByCode("CN_PROVINCE")).thenReturn(type(9L, "CN_PROVINCE"));
        when(dictItemMapper.selectEnabledByTypeId(9L)).thenReturn(List.of(
                item(101L, "510000", "四川省")
        ));

        assertThat(service.containsEnabledValue("CN_PROVINCE", " 510000 ")).isTrue();
        assertThat(service.findEnabledName("CN_PROVINCE", "510000")).contains("四川省");
        assertThat(service.containsEnabledValue("CN_PROVINCE", "990000")).isFalse();
    }

    @Test
    @DisplayName("空编码或未知字典返回空结果")
    void returnsEmptyForBlankOrUnknownDictionary() {
        assertThat(service.listEnabledItems("  ")).isEmpty();
        assertThat(service.containsEnabledValue("CN_PROVINCE", " ")).isFalse();
        verifyNoInteractions(dictTypeMapper, dictItemMapper);

        when(dictTypeMapper.selectEnabledByCode("UNKNOWN")).thenReturn(null);
        assertThat(service.listEnabledItems("UNKNOWN")).isEmpty();
        assertThat(service.findEnabledName("UNKNOWN", "510000")).isEqualTo(Optional.empty());
    }

    private SysDictTypeDO type(Long id, String code) {
        SysDictTypeDO type = new SysDictTypeDO();
        type.setId(id);
        type.setDictCode(code);
        type.setStatus(1);
        type.setDeleted(0);
        return type;
    }

    private SysDictItemDO item(Long id, String value, String label) {
        SysDictItemDO item = new SysDictItemDO();
        item.setId(id);
        item.setItemValue(value);
        item.setItemLabel(label);
        item.setStatus(1);
        item.setDeleted(0);
        return item;
    }
}
