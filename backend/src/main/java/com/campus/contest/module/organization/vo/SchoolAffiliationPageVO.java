package com.campus.contest.module.organization.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchoolAffiliationPageVO {

    private List<SchoolAffiliationItemVO> items = new ArrayList<>();
    private Long total;
    private Integer page;
    private Integer size;

    @JsonProperty("has_more")
    private Boolean hasMore;

    public static SchoolAffiliationPageVO empty(Integer page, Integer size) {
        return new SchoolAffiliationPageVO(new ArrayList<>(), 0L, page, size, false);
    }
}
