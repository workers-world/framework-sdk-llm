package com.books.framework.llm.dto;

import lombok.Data;

@Data
public class LLMLogQueryX1 {
    private Integer page;
    private Integer limit;
    private Boolean success;
    private String caller;
    private String provider;

    public int getPage() {
        return page != null && page > 0 ? page : 1;
    }

    public int getLimit() {
        return limit != null && limit > 0 ? Math.min(limit, 100) : 20;
    }
}
