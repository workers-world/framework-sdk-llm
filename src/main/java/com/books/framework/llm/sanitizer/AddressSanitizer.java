package com.books.framework.llm.sanitizer;

import java.util.regex.Pattern;

public class AddressSanitizer implements Sanitizer {

    private static final Pattern ADDRESS = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,}(?:省|市|区|县|镇|乡|街道|路|巷|号|栋|单元|室|层|楼|站|厦)[\\u4e00-\\u9fa5\\d]*");

    @Override
    public String name() {
        return "AddressSanitizer";
    }

    @Override
    public String displayName() {
        return "地址脱敏";
    }

    @Override
    public String sanitize(String text) {
        return ADDRESS.matcher(text).replaceAll("[地址已脱敏]");
    }

    @Override
    public int getOrder() {
        return 50;
    }
}
