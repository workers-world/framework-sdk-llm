package com.books.framework.llm.sanitizer;

import java.util.ArrayList;
import java.util.List;

public class SanitizerChain {
    private final List<Sanitizer> sanitizers;

    public SanitizerChain(List<Sanitizer> sanitizers) {
        this.sanitizers = new ArrayList<>(sanitizers);
        this.sanitizers.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
    }

    public String sanitize(String text, List<String> enabledNames) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Sanitizer s : sanitizers) {
            if (enabledNames == null || enabledNames.contains(s.name())) {
                result = s.sanitize(result);
            }
        }
        return result;
    }

    public List<Sanitizer> getSanitizers() {
        return sanitizers;
    }
}
