package com.books.framework.llm.controller;

import com.books.framework.annotation.ApiName;
import com.books.framework.llm.dto.LLMStatsQueryX1;
import com.books.framework.llm.log.LLMCallLogger;
import com.books.framework.message.BaseMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/llm/stats")
public class LLMStatsController {

    private final LLMCallLogger callLogger;

    public LLMStatsController(LLMCallLogger callLogger) {
        this.callLogger = callLogger;
    }

    @ApiName(name = "llmStats",
            input = {LLMStatsQueryX1.class},
            description = "调用统计")
    @PostMapping("/list")
    public BaseMessage stats(@RequestBody(required = false) BaseMessage request) {
        LLMStatsQueryX1 x1 = request.getDto(LLMStatsQueryX1.class);
        String from = x1 != null && x1.getFrom() != null ? x1.getFrom() : "2000-01-01";
        String to = x1 != null && x1.getTo() != null ? x1.getTo() : "2099-12-31";

        Map<String, Object> result = callLogger != null ? callLogger.stats(from, to) : Collections.emptyMap();

        return getResponse(request, result);
    }

    private BaseMessage getResponse(BaseMessage request, Map<String, Object> result) {
        BaseMessage response = request.newResponse();
        response.setMessage("查询成功");
        for (Map.Entry<String, Object> e : result.entrySet()) {
            response.put(e.getKey(), e.getValue());
        }
        return response;
    }
}
