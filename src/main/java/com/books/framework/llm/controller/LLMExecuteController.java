package com.books.framework.llm.controller;

import com.books.framework.annotation.ApiName;
import com.books.framework.constants.ErrorCodeEnum;
import com.books.framework.exception.AppException;
import com.books.framework.llm.LLMService;
import com.books.framework.llm.dto.LLMBatchExecuteX1;
import com.books.framework.llm.dto.LLMExecuteX1;
import com.books.framework.llm.dto.LLMResponseZ1;
import com.books.framework.llm.model.LLMCallOptions;
import com.books.framework.message.BaseMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/llm")
public class LLMExecuteController {

    private final LLMService llmService;

    public LLMExecuteController(LLMService llmService) {
        this.llmService = llmService;
    }

    @ApiName(name = "llmExecute",
            input = {LLMExecuteX1.class},
            output = {LLMResponseZ1.class},
            description = "执行大模型调用")
    @PostMapping("/execute")
    public BaseMessage execute(@RequestBody(required = false) BaseMessage request) {
        LLMExecuteX1 x1 = request.getDto(LLMExecuteX1.class);
        if (x1 == null || x1.getUserMessage() == null || x1.getUserMessage().trim().isEmpty()) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "userMessage 不能为空");
        }

        Prompt prompt = buildPrompt(x1);
        LLMCallOptions opts = buildCallOptions(x1);

        long start = System.currentTimeMillis();
        try {
            ChatResponse chatResp = llmService.execute(prompt, opts);
            String content = "";
            if (chatResp.getResults() != null && !chatResp.getResults().isEmpty()
                    && chatResp.getResults().get(0).getOutput() != null) {
                content = chatResp.getResults().get(0).getOutput().getText();
            }
            int tokens = 0;
            String model = null;
            if (chatResp.getMetadata() != null) {
                if (chatResp.getMetadata().getUsage() != null)
                    tokens = (int) chatResp.getMetadata().getUsage().getTotalTokens();
                model = chatResp.getMetadata().getModel();
            }
            long elapsed = System.currentTimeMillis() - start;

            BaseMessage response = request.newResponse();
            response.setMessage("调用成功");
            response.addDto(LLMResponseZ1.builder()
                    .content(content).success(true)
                    .tokensUsed(tokens).responseTimeMs(elapsed).modelName(model)
                    .build());
            return response;
        } catch (Exception e) {
            BaseMessage response = request.newResponse();
            response.setMessage("调用失败");
            response.addDto(LLMResponseZ1.builder()
                    .success(false).errorMessage(e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - start).build());
            return response;
        }
    }

    @ApiName(name = "llmExecuteStream", input = {LLMExecuteX1.class}, description = "流式大模型调用(SSE)")
    @PostMapping(value = "/execute-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> executeStream(@RequestBody(required = false) BaseMessage request) {
        LLMExecuteX1 x1 = request.getDto(LLMExecuteX1.class);
        if (x1 == null || x1.getUserMessage() == null || x1.getUserMessage().trim().isEmpty()) {
            return Flux.just("data: {\"error\":\"userMessage 不能为空\"}\n\n");
        }

        Prompt prompt = buildPrompt(x1);
        LLMCallOptions opts = buildCallOptions(x1);

        return llmService.executeStream(prompt, opts)
                .map(chunk -> {
                    if (chunk.getResults() != null && !chunk.getResults().isEmpty()
                            && chunk.getResults().get(0).getOutput() != null) {
                        String text = chunk.getResults().get(0).getOutput().getText();
                        return "data: " + text + "\n\n";
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .concatWithValues("data: [DONE]\n\n")
                .timeout(Duration.ofSeconds(300));
    }

    @ApiName(name = "llmExecuteBatch",
            input = {LLMBatchExecuteX1.class},
            description = "批量大模型调用")
    @PostMapping("/execute-batch")
    public BaseMessage executeBatch(@RequestBody(required = false) BaseMessage request) {
        LLMBatchExecuteX1 x1 = request.getDto(LLMBatchExecuteX1.class);
        if (x1 == null || x1.getRequests() == null || x1.getRequests().isEmpty()) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "requests 不能为空");
        }

        List<Prompt> prompts = new ArrayList<>();
        for (LLMExecuteX1 ex : x1.getRequests()) {
            prompts.add(buildPrompt(ex));
        }
        LLMCallOptions opts = buildCallOptions(x1.getRequests().get(0));

        List<ChatResponse> results = llmService.executeBatch(prompts, opts);
        List<LLMResponseZ1> z1List = new ArrayList<>();
        for (ChatResponse r : results) {
            String c = r != null && r.getResults() != null && !r.getResults().isEmpty()
                    ? r.getResults().get(0).getOutput().getText() : "";
            z1List.add(LLMResponseZ1.builder().success(true).content(c).build());
        }

        BaseMessage response = request.newResponse();
        response.setMessage("批量调用完成");
        response.addDtoList(z1List);
        return response;
    }

    private Prompt buildPrompt(LLMExecuteX1 x1) {
        List<Message> msgs = new ArrayList<>();
        if (x1.getSystemPrompt() != null && !x1.getSystemPrompt().isEmpty()) {
            msgs.add(new SystemMessage(x1.getSystemPrompt()));
        }

        // 多模态: 如果有 mediaUrls 则构建带 Media 的 UserMessage
        if (x1.getMediaUrls() != null && !x1.getMediaUrls().isEmpty()
                && x1.getMediaUrls().stream().anyMatch(u -> u != null && !u.isEmpty())) {
            List<Media> mediaList = x1.getMediaUrls().stream()
                    .filter(u -> u != null && !u.isEmpty())
                    .map(u -> Media.builder().mimeType(new MimeType("image")).data(java.net.URI.create(u)).build())
                    .toList();
            msgs.add(UserMessage.builder().text(x1.getUserMessage()).media(mediaList).build());
        } else {
            msgs.add(new UserMessage(x1.getUserMessage()));
        }

        OpenAiChatOptions.Builder optsBuilder = OpenAiChatOptions.builder()
                .maxTokens(x1.getMaxTokens() > 0 ? x1.getMaxTokens() : 256)
                .temperature(x1.getTemperature());

        if (x1.getModel() != null && !x1.getModel().isEmpty()) {
            optsBuilder.model(x1.getModel());
        }

        if ("JSON_SCHEMA".equals(x1.getResponseFormat()) && x1.getJsonSchema() != null) {
            optsBuilder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA).jsonSchema(x1.getJsonSchema()).build());
        } else if ("JSON".equals(x1.getResponseFormat())) {
            optsBuilder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT).build());
        }
        return new Prompt(msgs, optsBuilder.build());
    }

    private LLMCallOptions buildCallOptions(LLMExecuteX1 x1) {
        return LLMCallOptions.builder()
                .provider(x1.getProvider())
                .model(x1.getModel())
                .skipSanitize(x1.isSkipSanitize())
                .toolNames(x1.getToolNames())
                .enabledSanitizers(x1.getEnabledSanitizers())
                .idempotencyKey(x1.getIdempotencyKey())
                .metadata(x1.getMetadata())
                .build();
    }
}
