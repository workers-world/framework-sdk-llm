package com.books.framework.llm.sanitizer;

import com.books.framework.utils.HanLPKeywordExtractor;

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
        return keywordExtractor.maskPersonNames(text);
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
