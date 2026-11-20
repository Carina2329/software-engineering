package com.campus.contest.module.contest.vo;

import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EditionItemVO(
        String id,
        @JsonProperty("edition_name") String editionName,
        int year,
        String status,
        @JsonProperty("owner_org_id") String ownerOrgId,
        @JsonProperty("owner_org_name") String ownerOrgName,
        String level,
        @JsonProperty("school_id") String schoolId,
        @JsonProperty("province_code") String provinceCode,
        String visibility
) {
    public static EditionItemVO from(ContestEditionDO edition, String ownerOrgName) {
        return new EditionItemVO(
                String.valueOf(edition.getId()),
                edition.getEditionName(),
                edition.getYear(),
                EditionStatus.fromDatabaseValue(edition.getStatus()).name(),
                edition.getOwnerOrgId() == null ? null : String.valueOf(edition.getOwnerOrgId()),
                ownerOrgName,
                edition.getLevel(),
                edition.getSchoolId() == null ? null : String.valueOf(edition.getSchoolId()),
                edition.getProvinceCode(),
                edition.getVisibility()
        );
    }
}
