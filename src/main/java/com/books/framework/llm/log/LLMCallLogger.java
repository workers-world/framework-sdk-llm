package com.books.framework.llm.log;

import java.util.List;
import java.util.Map;

public interface LLMCallLogger {
    void log(LLMCallLogEntry entry);
    LLMCallLogEntry findByIdempotencyKey(String idempotencyKey);
    Map<String, Object> findPage(String caller, String provider, Boolean success, int page, int limit);
    LLMCallLogEntry findById(Long id);
    Map<String, Object> stats(String from, String to);
    List<Map<String, Object>> healthOverview();
}
