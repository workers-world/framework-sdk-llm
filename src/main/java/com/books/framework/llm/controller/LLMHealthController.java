package com.books.framework.llm.controller;

import com.books.framework.annotation.ApiName;
import com.books.framework.llm.dto.LLMHealthCheckX1;
import com.books.framework.llm.log.LLMCallLogger;
import com.books.framework.message.BaseMessage;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/llm/health")
public class LLMHealthController {

    private final LLMCallLogger callLogger;

    public LLMHealthController(LLMCallLogger callLogger) {
        this.callLogger = callLogger;
    }

    @ApiName(name = "llmHealthOverview", description = "所有后端连通性概览")
    @PostMapping("/list")
    public BaseMessage overview(@RequestBody(required = false) BaseMessage request) {
        BaseMessage response = request != null ? request.newResponse() : new BaseMessage();
        if (response.getCode() == null) response.setCode("SUCCESS");
        response.setMessage("查询成功");
        response.put("list", callLogger != null ? callLogger.healthOverview() : Collections.emptyList());
        return response;
    }

    @ApiName(name = "llmHealthCheck", input = {LLMHealthCheckX1.class}, description = "单个后端连通性检查")
    @PostMapping("/check")
    public BaseMessage check(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new RuntimeException("请求报文不能为空");
        }
        LLMHealthCheckX1 x1 = request.getDto(LLMHealthCheckX1.class);
        if (x1 == null || x1.getProvider() == null) {
            throw new RuntimeException("provider 不能为空");
        }

        List<Map<String, Object>> recent = callLogger != null ? callLogger.healthOverview() : Collections.emptyList();
        boolean reachable = false;
        String lastModel = null;
        for (Map<String, Object> row : recent) {
            if (x1.getProvider().equals(row.get("provider"))) {
                reachable = true;
                lastModel = (String) row.get("model_name");
                break;
            }
        }

        BaseMessage response = request.newResponse();
        response.setMessage("查询成功");
        response.put("provider", x1.getProvider());
        response.put("reachable", reachable);
        response.put("lastModel", lastModel);
        return response;
    }
}
