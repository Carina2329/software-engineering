package com.campus.contest.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.system.entity.SysDictTypeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictTypeDO> {

    @Select("""
            SELECT id, dict_code, dict_name, remark, status,
                   created_at, created_by, updated_at, updated_by,
                   deleted, deleted_at, version
            FROM sys_dict_type
            WHERE dict_code = #{dictCode}
              AND status = 1
              AND deleted = 0
            ORDER BY id
            LIMIT 1
            """)
    SysDictTypeDO selectEnabledByCode(@Param("dictCode") String dictCode);
}
