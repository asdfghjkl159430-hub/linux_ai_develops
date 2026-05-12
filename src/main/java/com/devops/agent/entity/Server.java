package com.devops.agent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Server {
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String osType;
    private String tags;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
