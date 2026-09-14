package com.campus.contest.common;

import lombok.Data;

/**
 * 分页查询基类：所有列表查询的 XxxQuery 继承它。
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
