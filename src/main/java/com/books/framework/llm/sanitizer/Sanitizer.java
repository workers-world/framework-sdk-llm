package com.books.framework.llm.sanitizer;

import org.springframework.core.Ordered;

public interface Sanitizer extends Ordered {
    String name();
    String displayName();
    String sanitize(String text);

    @Override
    default int getOrder() {
        return 0;
    }
}
