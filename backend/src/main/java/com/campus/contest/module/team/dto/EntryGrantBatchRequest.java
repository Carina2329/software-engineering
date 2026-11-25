package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EntryGrantBatchRequest {

    @Valid
    @NotEmpty
    @Size(max = 200)
    @JsonProperty("items")
    private List<EntryGrantBatchItem> items;
}
