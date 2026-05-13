package com.books.framework.llm.dto;

import lombok.Data;

import java.util.List;

@Data
public class LLMBatchExecuteX1 {
    private List<LLMExecuteX1> requests;
}
