package com.books.framework.llm.sanitizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonNameSanitizer implements Sanitizer {

    private static final Pattern NAME_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}(?=，|。|！|\\s|$)");
    private int counter = 0;

    @Override
    public String name() {
        return "PersonNameSanitizer";
    }

    @Override
    public String displayName() {
        return "中文姓名脱敏";
    }

    @Override
    public String sanitize(String text) {
        // 仅处理紧随标点的姓名模式，避免误伤普通词汇
        StringBuffer sb = new StringBuffer();
        Matcher m = NAME_PATTERN.matcher(text);
        while (m.find()) {
            m.appendReplacement(sb, "个人用户" + (char) ('A' + (counter++ % 26)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
