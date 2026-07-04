package com.books.framework.llm.image;

import org.springframework.ai.image.ImageOptions;

import java.util.Collections;
import java.util.List;

public class WorkerImageOptions implements ImageOptions {

    private final Integer n;
    private final String model;
    private final Integer width;
    private final Integer height;
    private final String responseFormat;
    private final String style;
    private final String aspectRatio;
    private final Integer maxSize;
    private final Integer seed;
    private final Integer steps;
    private final String personImage;
    private final List<String> garmentImages;
    private final Boolean turbo;
    private final String outputFormat;

    private WorkerImageOptions(Builder builder) {
        this.n = builder.n;
        this.model = builder.model;
        this.width = builder.width;
        this.height = builder.height;
        this.responseFormat = builder.responseFormat;
        this.style = builder.style;
        this.aspectRatio = builder.aspectRatio;
        this.maxSize = builder.maxSize;
        this.seed = builder.seed;
        this.steps = builder.steps;
        this.personImage = builder.personImage;
        this.garmentImages = builder.garmentImages == null
                ? null
                : List.copyOf(builder.garmentImages);
        this.turbo = builder.turbo;
        this.outputFormat = builder.outputFormat;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Integer getN() {
        return n;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Integer getWidth() {
        return width;
    }

    @Override
    public Integer getHeight() {
        return height;
    }

    @Override
    public String getResponseFormat() {
        return responseFormat;
    }

    @Override
    public String getStyle() {
        return style;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public Integer getSeed() {
        return seed;
    }

    public Integer getSteps() {
        return steps;
    }

    public String getPersonImage() {
        return personImage;
    }

    public List<String> getGarmentImages() {
        return garmentImages == null ? null : Collections.unmodifiableList(garmentImages);
    }

    public Boolean getTurbo() {
        return turbo;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public static WorkerImageOptions from(ImageOptions options) {
        if (options instanceof WorkerImageOptions workerOptions) {
            return workerOptions;
        }
        return builder()
                .model(options.getModel())
                .width(options.getWidth())
                .height(options.getHeight())
                .n(options.getN())
                .responseFormat(options.getResponseFormat())
                .style(options.getStyle())
                .build();
    }

    public static final class Builder {
        private Integer n;
        private String model;
        private Integer width;
        private Integer height;
        private String responseFormat;
        private String style;
        private String aspectRatio;
        private Integer maxSize;
        private Integer seed;
        private Integer steps;
        private String personImage;
        private List<String> garmentImages;
        private Boolean turbo;
        private String outputFormat;

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder width(Integer width) {
            this.width = width;
            return this;
        }

        public Builder height(Integer height) {
            this.height = height;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder style(String style) {
            this.style = style;
            return this;
        }

        public Builder aspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        public Builder maxSize(Integer maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder steps(Integer steps) {
            this.steps = steps;
            return this;
        }

        public Builder personImage(String personImage) {
            this.personImage = personImage;
            return this;
        }

        public Builder garmentImages(List<String> garmentImages) {
            this.garmentImages = garmentImages;
            return this;
        }

        public Builder turbo(Boolean turbo) {
            this.turbo = turbo;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        public WorkerImageOptions build() {
            return new WorkerImageOptions(this);
        }
    }
}
