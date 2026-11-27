package com.erp.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TodoItemDTO {
    private Long id;
    private String type;
    private String typeName;
    private String billNo;
    private String title;
    private String status;
    private LocalDateTime createTime;
    private String creator;
}
