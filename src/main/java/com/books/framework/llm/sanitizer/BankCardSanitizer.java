package com.books.framework.llm.sanitizer;

import java.util.regex.Pattern;

public class BankCardSanitizer implements Sanitizer {

    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,19}\\b");

    @Override
    public String name() {
        return "BankCardSanitizer";
    }

    @Override
    public String displayName() {
        return "银行卡号脱敏";
    }

    @Override
    public String sanitize(String text) {
        return BANK_CARD.matcher(text).replaceAll("****");
    }

    @Override
    public int getOrder() {
        return 30;
    }
}
