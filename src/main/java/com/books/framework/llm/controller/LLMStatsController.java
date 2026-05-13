package com.books.framework.llm.controller;

import com.books.framework.annotation.ApiName;
import com.books.framework.llm.dto.LLMStatsQueryX1;
import com.books.framework.llm.log.LLMCallLogger;
import com.books.framework.message.BaseMessage;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/llm/stats")
public class LLMStatsController {

    private final LLMCallLogger callLogger;

    public LLMStatsController(LLMCallLogger callLogger) {
        this.callLogger = callLogger;
    }

    @ApiName(name = "llmStats", input = {LLMStatsQueryX1.class}, description = "调用统计")
    @PostMapping("/list")
    public BaseMessage stats(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new RuntimeException("请求报文不能为空");
        }
        LLMStatsQueryX1 x1 = request.getDto(LLMStatsQueryX1.class);
        String from = x1 != null && x1.getFrom() != null ? x1.getFrom() : "2000-01-01";
        String to = x1 != null && x1.getTo() != null ? x1.getTo() : "2099-12-31";

        Map<String, Object> result = callLogger != null ? callLogger.stats(from, to) : java.util.Collections.emptyMap();
        BaseMessage response = request.newResponse();
        response.setMessage("查询成功");
        for (Map.Entry<String, Object> e : result.entrySet()) {
            response.put(e.getKey(), e.getValue());
        }
        return response;
    }
}
