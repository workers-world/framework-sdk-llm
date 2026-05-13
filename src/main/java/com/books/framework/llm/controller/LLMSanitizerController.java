package com.books.framework.llm.controller;

import com.books.framework.annotation.ApiName;
import com.books.framework.llm.dto.LLMSanitizerToggleX1;
import com.books.framework.llm.sanitizer.Sanitizer;
import com.books.framework.llm.sanitizer.SanitizerRegistry;
import com.books.framework.message.BaseMessage;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/llm/sanitizers")
public class LLMSanitizerController {

    private final List<Sanitizer> sanitizers;
    private final SanitizerRegistry registry;

    public LLMSanitizerController(List<Sanitizer> sanitizers, SanitizerRegistry registry) {
        this.sanitizers = sanitizers;
        this.registry = registry;
    }

    @ApiName(name = "llmListSanitizers", description = "列出所有脱敏处理器")
    @PostMapping("/list")
    public BaseMessage list(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            // 无请求也可查询
            BaseMessage response = new BaseMessage();
            response.setCode("SUCCESS");
            response.setMessage("查询成功");
            response.put("list", buildList());
            return response;
        }
        BaseMessage response = request.newResponse();
        response.setMessage("查询成功");
        response.put("list", buildList());
        return response;
    }

    @ApiName(name = "llmToggleSanitizer", input = {LLMSanitizerToggleX1.class}, description = "启用/禁用脱敏处理器")
    @PostMapping("/toggle")
    public BaseMessage toggle(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new RuntimeException("请求报文不能为空");
        }
        LLMSanitizerToggleX1 x1 = request.getDto(LLMSanitizerToggleX1.class);

        // 从 data 中手动取 name（name 不在 X1 中，需特殊处理）
        Map<String, Object> data = request.getData();
        String name = data != null ? (String) data.get("name") : null;
        if (name == null || name.isEmpty()) {
            throw new RuntimeException("name 不能为空");
        }

        boolean enabled = x1 != null && Boolean.TRUE.equals(x1.getEnabled());
        registry.setEnabled(name, enabled);
        BaseMessage response = request.newResponse();
        response.setMessage(enabled ? "已启用" : "已禁用");
        response.put("name", name);
        response.put("enabled", enabled);
        return response;
    }

    private List<Map<String, Object>> buildList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Sanitizer s : sanitizers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", s.name());
            item.put("displayName", s.displayName());
            item.put("enabled", registry.isEnabled(s.name()));
            item.put("order", s.getOrder());
            result.add(item);
        }
        return result;
    }
}
