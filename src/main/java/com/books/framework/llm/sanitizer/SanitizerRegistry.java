package com.books.framework.llm.sanitizer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SanitizerRegistry {

    private final Set<String> disabledNames = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(String name) {
        return !disabledNames.contains(name);
    }

    public void setEnabled(String name, boolean enabled) {
        if (enabled) {
            disabledNames.remove(name);
        } else {
            disabledNames.add(name);
        }
    }

    public Set<String> getDisabledNames() {
        return Collections.unmodifiableSet(disabledNames);
    }

    public List<String> getDefaultEnabled(List<Sanitizer> all) {
        List<String> names = new ArrayList<>();
        for (Sanitizer s : all) {
            if (isEnabled(s.name())) {
                names.add(s.name());
            }
        }
        return names;
    }
}
