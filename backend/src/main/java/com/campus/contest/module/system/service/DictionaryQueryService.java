package com.campus.contest.module.system.service;

import java.util.List;
import java.util.Optional;

/**
 * 启用状态字典的公共只读查询能力。
 */
public interface DictionaryQueryService {

    /**
     * 按字典编码查询全部启用项，按配置顺序返回。
     */
    List<DictionaryItem> listEnabledItems(String dictCode);

    /**
     * 判断指定取值是否属于启用字典。
     */
    boolean containsEnabledValue(String dictCode, String itemValue);

    /**
     * 根据字典编码和取值查询启用项的展示名称。
     */
    Optional<String> findEnabledName(String dictCode, String itemValue);

    record DictionaryItem(String id, String code, String name) {
    }
}
