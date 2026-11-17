package com.campus.contest.module.contest.vo;

import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EditionItemVO(
        String id,
        @JsonProperty("edition_name") String editionName,
        int year,
        String status
) {
    public static EditionItemVO from(ContestEditionDO edition) {
        return new EditionItemVO(
                String.valueOf(edition.getId()),
                edition.getEditionName(),
                edition.getYear(),
                EditionStatus.fromDatabaseValue(edition.getStatus()).name()
        );
    }
}
