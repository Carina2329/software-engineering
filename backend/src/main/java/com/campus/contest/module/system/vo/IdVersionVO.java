package com.campus.contest.module.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EP-007 / EP-075 写操作响应：资源 ID 与更新后的乐观锁版本号。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdVersionVO {

    private String id;

    private Integer version;
}
