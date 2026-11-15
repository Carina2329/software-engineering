package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * EP-005 分页响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPageVO {

    private List<UserItemVO> items = new ArrayList<>();

    private Long total;

    private Integer page;

    private Integer size;

    @JsonProperty("has_more")
    private Boolean hasMore;

    public static UserPageVO empty(Integer page, Integer size) {
        return new UserPageVO(new ArrayList<>(), 0L, page, size, false);
    }
}
