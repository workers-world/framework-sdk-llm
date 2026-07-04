package com.books.framework.llm.image;

import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;

public final class WorkerImageResponses {

    public static final String META_KEY = "key";
    public static final String META_MODEL = "model";

    private WorkerImageResponses() {
    }

    public static String key(ImageResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Object value = response.getMetadata().get(META_KEY);
        return value == null ? null : String.valueOf(value);
    }

    public static String model(ImageResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Object value = response.getMetadata().get(META_MODEL);
        return value == null ? null : String.valueOf(value);
    }

    public static Long created(ImageResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        ImageResponseMetadata metadata = response.getMetadata();
        return metadata.getCreated();
    }
}
