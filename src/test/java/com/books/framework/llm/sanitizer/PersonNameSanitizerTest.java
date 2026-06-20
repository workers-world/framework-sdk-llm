package com.books.framework.llm.sanitizer;

import com.books.framework.utils.HanLPKeywordExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersonNameSanitizerTest {

    private final PersonNameSanitizer sanitizer = new PersonNameSanitizer(new HanLPKeywordExtractor());

    @Test
    void keepsShortGreeting() {
        assertEquals("你好", sanitizer.sanitize("你好"));
    }

    @Test
    void keepsGreetingWithPunctuation() {
        assertEquals("你好！", sanitizer.sanitize("你好！"));
    }

    @Test
    void masksRecognizedPersonNameWhenTagged() {
        String masked = sanitizer.sanitize("转账给王伟，金额100元");
        if (!masked.contains("王伟")) {
            assertTrue(masked.contains("个人用户"));
        }
    }
}
