package com.devops.agent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskLog {
    private Long id;
    private Long taskId;
    private Integer step;
    private String command;
    private String output;
    private Integer exitCode;
    private String aiReasoning;
    private Integer status;
    private LocalDateTime createdAt;
}
