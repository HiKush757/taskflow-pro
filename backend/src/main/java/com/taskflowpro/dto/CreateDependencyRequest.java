package com.taskflowpro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDependencyRequest {
    @NotBlank
    private String prerequisiteId;
}
