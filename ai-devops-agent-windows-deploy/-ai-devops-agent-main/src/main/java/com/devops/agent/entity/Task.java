package com.devops.agent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Task {
    private Long id;
    private String userInput;
    private String intent;
    private Long serverId;
    private Integer status;
    private String agentType;
    private String resultSummary;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
