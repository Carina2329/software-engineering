package com.campus.contest.module.contest.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface ContestAccessMapper {

    @SelectProvider(type = ContestAccessSqlProvider.class, method = "selectContestPage")
    IPage<ContestDO> selectContestPage(
            Page<ContestDO> page,
            @Param("criteria") ContestAccessCriteria criteria
    );

    @SelectProvider(type = ContestAccessSqlProvider.class, method = "selectContestById")
    ContestDO selectContestById(@Param("criteria") ContestAccessCriteria criteria);

    @SelectProvider(type = ContestAccessSqlProvider.class, method = "selectEditionList")
    List<ContestEditionDO> selectEditionList(@Param("criteria") ContestAccessCriteria criteria);

    @SelectProvider(type = ContestAccessSqlProvider.class, method = "selectEditionPage")
    IPage<ContestEditionDO> selectEditionPage(
            Page<ContestEditionDO> page,
            @Param("criteria") ContestAccessCriteria criteria
    );

    @SelectProvider(type = ContestAccessSqlProvider.class, method = "selectEditionById")
    ContestEditionDO selectEditionById(@Param("criteria") ContestAccessCriteria criteria);
}
