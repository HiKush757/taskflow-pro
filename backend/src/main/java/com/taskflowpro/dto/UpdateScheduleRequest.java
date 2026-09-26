package com.taskflowpro.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateScheduleRequest {
    private LocalDate plannedStart;
    private Integer durationDays;
}
