package com.books.framework.llm.sanitizer;

import java.util.regex.Pattern;

public class OrderIdSanitizer implements Sanitizer {

    private static final Pattern LONG_DIGITS = Pattern.compile("\\b\\d{10,}\\b");

    @Override
    public String name() {
        return "OrderIdSanitizer";
    }

    @Override
    public String displayName() {
        return "订单号脱敏";
    }

    @Override
    public String sanitize(String text) {
        return LONG_DIGITS.matcher(text).replaceAll("ORDER-****");
    }

    @Override
    public int getOrder() {
        return 40;
    }
}
