package com.books.framework.llm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class LLMConfigIdX1 {
    @NotNull
    @Positive
    private Long id;
}
