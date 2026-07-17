package com.books.framework.llm.image;

import com.books.framework.http.FrameworkHttpClient;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WorkerGatewayImageModel implements ImageModel {

    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    private WorkerGatewayImageModel(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.apiKey;
        this.defaultModel = builder.defaultModel;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ImageResponse call(ImagePrompt imagePrompt) {
        WorkerImageOptions options = resolveOptions(imagePrompt);
        String prompt = resolvePrompt(imagePrompt);
        Map<String, Object> body = buildRequestBody(options, prompt);

        FrameworkHttpClient client = FrameworkHttpClient.builder()
                .baseUrl(baseUrl)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();

        Map<String, String> headers = new HashMap<>();
        if (StringUtils.hasText(apiKey)) {
            headers.put("Authorization", "Bearer " + apiKey.trim());
        }

        Map<String, Object> resp;
        try {
            resp = client.post("/images/generations", body, Map.class, headers);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new WorkerGatewayImageException("图片生成失败: " + msg, e);
        }

        if (resp == null) {
            throw new WorkerGatewayImageException("Worker 返回空响应");
        }
        if (resp.containsKey("error")) {
            throw new WorkerGatewayImageException(String.valueOf(resp.get("error")));
        }

        String key = Objects.requireNonNullElse(
                resp.get("key") == null ? null : String.valueOf(resp.get("key")), "");
        String model = Objects.requireNonNullElse(
                resp.get("model") == null ? options.getModel() : String.valueOf(resp.get("model")), defaultModel);
        Long created = null;
        Object createdRaw = resp.get("created");
        if (createdRaw instanceof Number number) {
            created = number.longValue();
        }
        created = Objects.requireNonNullElse(created, 0L);

        ImageResponseMetadata metadata = new ImageResponseMetadata(created);
        metadata.put(WorkerImageResponses.META_KEY, key);
        metadata.put(WorkerImageResponses.META_MODEL, model);

        ImageGeneration generation = new ImageGeneration(new Image(null, null));
        return new ImageResponse(List.of(generation), metadata);
    }

    private WorkerImageOptions resolveOptions(ImagePrompt imagePrompt) {
        ImageOptions raw = imagePrompt.getOptions();
        WorkerImageOptions options = raw == null
                ? WorkerImageOptions.builder().build()
                : WorkerImageOptions.from(raw);
        if (!StringUtils.hasText(options.getModel())) {
            options = WorkerImageOptions.builder()
                    .model(defaultModel)
                    .width(options.getWidth())
                    .height(options.getHeight())
                    .n(options.getN())
                    .responseFormat(options.getResponseFormat())
                    .style(options.getStyle())
                    .aspectRatio(options.getAspectRatio())
                    .maxSize(options.getMaxSize())
                    .seed(options.getSeed())
                    .steps(options.getSteps())
                    .personImage(options.getPersonImage())
                    .garmentImages(options.getGarmentImages())
                    .turbo(options.getTurbo())
                    .outputFormat(options.getOutputFormat())
                    .build();
        }
        return options;
    }

    private static String resolvePrompt(ImagePrompt imagePrompt) {
        List<ImageMessage> messages = imagePrompt.getInstructions();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ImageMessage message : messages) {
            if (message == null || !StringUtils.hasText(message.getText())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(message.getText().trim());
        }
        return sb.toString();
    }

    private Map<String, Object> buildRequestBody(WorkerImageOptions options, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (StringUtils.hasText(prompt)) {
            body.put("prompt", prompt);
        }
        body.put("model", options.getModel());
        if (StringUtils.hasText(options.getAspectRatio())) {
            body.put("aspect_ratio", options.getAspectRatio());
        }
        if (options.getMaxSize() != null) {
            body.put("max_size", options.getMaxSize());
        }
        if (options.getWidth() != null) {
            body.put("width", options.getWidth());
        }
        if (options.getHeight() != null) {
            body.put("height", options.getHeight());
        }
        if (options.getSeed() != null) {
            body.put("seed", options.getSeed());
        }
        if (options.getSteps() != null) {
            body.put("steps", options.getSteps());
        }
        if (StringUtils.hasText(options.getPersonImage())) {
            body.put("person_image", options.getPersonImage());
        }
        if (options.getGarmentImages() != null && !options.getGarmentImages().isEmpty()) {
            body.put("garment_images", options.getGarmentImages());
        }
        if (options.getTurbo() != null) {
            body.put("turbo", options.getTurbo());
        }
        if (StringUtils.hasText(options.getOutputFormat())) {
            body.put("output_format", options.getOutputFormat());
        }
        return body;
    }

    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private String defaultModel = "@cf/black-forest-labs/flux-2-klein-4b";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(120);

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public WorkerGatewayImageModel build() {
            if (!StringUtils.hasText(baseUrl)) {
                throw new IllegalStateException("Worker baseUrl 未配置");
            }
            return new WorkerGatewayImageModel(this);
        }
    }
}
