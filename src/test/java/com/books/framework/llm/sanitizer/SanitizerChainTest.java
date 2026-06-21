package com.books.framework.llm.sanitizer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SanitizerChainTest {

    @Test
    void sanitizesWithEnabledSanitizers() {
        Sanitizer s1 = new Sanitizer() {
            public String name() { return "s1"; }
            public String displayName() { return "d1"; }
            public String sanitize(String text) { return text.replace("敏感", "**[脱敏]**"); }
        };
        Sanitizer s2 = new Sanitizer() {
            public String name() { return "s2"; }
            public String displayName() { return "d2"; }
            public String sanitize(String text) { return text.replace("12345", "*****"); }
            public int getOrder() { return 5; }
        };

        SanitizerChain chain = new SanitizerChain(List.of(s1, s2));
        String result = chain.sanitize("账号敏感信息12345", List.of("s1", "s2"));
        assertEquals("账号**[脱敏]**信息*****", result);
    }

    @Test
    void skipsDisabledSanitizers() {
        Sanitizer s1 = new Sanitizer() {
            public String name() { return "s1"; }
            public String displayName() { return "d1"; }
            public String sanitize(String text) { return text.replace("A", "X"); }
        };

        SanitizerChain chain = new SanitizerChain(List.of(s1));
        assertEquals("ABC", chain.sanitize("ABC", List.of("s2"))); // s1 not in enabled list
        assertEquals("XBC", chain.sanitize("ABC", List.of("s1"))); // s1 enabled
    }

    @Test
    void respectsOrder() {
        Sanitizer first = new Sanitizer() {
            public String name() { return "first"; }
            public String displayName() { return "f"; }
            public String sanitize(String text) { return "[" + text + "]"; }
            public int getOrder() { return 1; }
        };
        Sanitizer second = new Sanitizer() {
            public String name() { return "second"; }
            public String displayName() { return "s"; }
            public String sanitize(String text) { return text + "!"; }
            public int getOrder() { return 2; }
        };

        SanitizerChain chain = new SanitizerChain(List.of(second, first));
        assertEquals("[hello]!", chain.sanitize("hello", List.of("first", "second")));
    }

    @Test
    void handlesNullText() {
        SanitizerChain chain = new SanitizerChain(List.of());
        assertNull(chain.sanitize(null, List.of()));
    }

    @Test
    void handlesEmptyText() {
        SanitizerChain chain = new SanitizerChain(List.of());
        assertEquals("", chain.sanitize("", List.of()));
    }

    @Test
    void allSanitizersWhenEnabledNamesNull() {
        Sanitizer s1 = new Sanitizer() {
            public String name() { return "s1"; }
            public String displayName() { return "d1"; }
            public String sanitize(String text) { return text.toLowerCase(); }
        };
        SanitizerChain chain = new SanitizerChain(List.of(s1));
        assertEquals("hello", chain.sanitize("HELLO", null));
    }
}
