package com.books.framework.llm.controller;

import com.books.framework.annotation.ApiName;
import com.books.framework.constants.ErrorCodeEnum;
import com.books.framework.exception.AppException;
import com.books.framework.llm.dto.LLMLogQueryX1;
import com.books.framework.llm.log.LLMCallLogEntry;
import com.books.framework.llm.log.LLMCallLogger;
import com.books.framework.message.BaseMessage;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/llm/logs")
public class LLMLogController {

    private final LLMCallLogger callLogger;

    public LLMLogController(LLMCallLogger callLogger) {
        this.callLogger = callLogger;
    }

    @ApiName(name = "llmListLogs", input = {LLMLogQueryX1.class}, description = "分页查询调用日志")
    @PostMapping("/list")
    public BaseMessage list(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        if (callLogger == null) {
            BaseMessage response = request.newResponse();
            response.setMessage("查询成功");
            response.put("list", Collections.emptyList());
            response.put("total", 0L);
            response.put("page", 1);
            return response;
        }
        LLMLogQueryX1 x1 = request.getDto(LLMLogQueryX1.class);
        int page = x1 != null ? x1.getPage() : 1;
        int limit = x1 != null ? x1.getLimit() : 20;
        String caller = x1 != null ? x1.getCaller() : null;
        String provider = x1 != null ? x1.getProvider() : null;
        Boolean success = x1 != null ? x1.getSuccess() : null;

        Map<String, Object> result = callLogger.findPage(caller, provider, success, page, limit);

        BaseMessage response = request.newResponse();
        response.setMessage("查询成功");
        response.put("list", result.get("data"));
        response.put("total", result.get("total"));
        response.put("page", result.get("page"));
        return response;
    }

    @ApiName(name = "llmGetLog", description = "查询单条日志详情")
    @PostMapping("/get")
    public BaseMessage getById(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        if (callLogger == null) {
            throw new AppException(ErrorCodeEnum.DATA_NOT_FOUND, "日志功能不可用");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = request.getDto(Map.class);
        if (data == null || !data.containsKey("id")) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "id 不能为空");
        }
        Long id = Long.valueOf(data.get("id").toString());
        LLMCallLogEntry entry = callLogger.findById(id);
        if (entry == null) {
            throw new AppException(ErrorCodeEnum.DATA_NOT_FOUND, "日志不存在");
        }
        BaseMessage response = request.newResponse();
        response.setMessage("查询成功");
        response.addDto(entry);
        return response;
    }
}
