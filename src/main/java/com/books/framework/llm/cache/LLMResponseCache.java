package com.books.framework.llm.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.model.ChatResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

public class LLMResponseCache {

    private final Cache<String, ChatResponse> cache;

    public LLMResponseCache(long maxSize, long ttlSeconds) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
    }

    public ChatResponse get(String provider, String model, String promptText) {
        return cache.getIfPresent(buildKey(provider, model, promptText));
    }

    public void put(String provider, String model, String promptText, ChatResponse response) {
        cache.put(buildKey(provider, model, promptText), response);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    private String buildKey(String provider, String model, String promptText) {
        String raw = provider + "|" + (model != null ? model : "") + "|" + promptText;
        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
