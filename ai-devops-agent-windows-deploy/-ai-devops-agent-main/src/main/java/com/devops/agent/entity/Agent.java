package com.devops.agent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Agent {
    private Long id;
    private String name;
    private String agentType;
    private String description;
    private String systemPrompt;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
