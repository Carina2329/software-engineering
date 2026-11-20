package com.campus.contest.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.system.entity.SysDictItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItemDO> {

    @Select("""
            SELECT id, dict_type_id, item_label, item_value, sort_no, status,
                   created_at, created_by, updated_at, updated_by,
                   deleted, deleted_at, version
            FROM sys_dict_item
            WHERE dict_type_id = #{dictTypeId}
              AND status = 1
              AND deleted = 0
            ORDER BY sort_no, id
            """)
    List<SysDictItemDO> selectEnabledByTypeId(@Param("dictTypeId") Long dictTypeId);
}
