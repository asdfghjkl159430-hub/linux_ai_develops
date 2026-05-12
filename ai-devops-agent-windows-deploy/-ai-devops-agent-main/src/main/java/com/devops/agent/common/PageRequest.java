package com.devops.agent.common;

import lombok.Data;

@Data
public class PageRequest {
    private int page = 1;
    private int size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}
