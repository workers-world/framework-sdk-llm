package com.books.framework.llm.sanitizer;

import java.util.regex.Pattern;

public class PhoneNumberSanitizer implements Sanitizer {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    @Override
    public String name() {
        return "PhoneNumberSanitizer";
    }

    @Override
    public String displayName() {
        return "手机号脱敏";
    }

    @Override
    public String sanitize(String text) {
        return PHONE.matcher(text).replaceAll(m -> m.group().substring(0, 3) + "****" + m.group().substring(7));
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
