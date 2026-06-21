package com.books.framework.llm.sanitizer;

import com.books.framework.utils.HanLPKeywordExtractor;

import java.util.List;
import java.util.Map;

public class PersonNameSanitizer implements Sanitizer {

    private final HanLPKeywordExtractor keywordExtractor;

    public PersonNameSanitizer() {
        this(new HanLPKeywordExtractor());
    }

    public PersonNameSanitizer(HanLPKeywordExtractor keywordExtractor) {
        this.keywordExtractor = keywordExtractor;
    }

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
        if (text == null || text.isEmpty()) return text;
        Map<String, List<String>> grouped = keywordExtractor.extractGrouped(text);
        List<String> names = grouped != null ? grouped.get("personNames") : null;
        if (names == null || names.isEmpty()) return text;
        String result = text;
        for (String name : names) {
            result = result.replace(name, "**[姓名]**");
        }
        return result;
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
