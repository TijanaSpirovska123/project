package com.example.marketing.infrastructure.util;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class FormBuilder<T> {
    private final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

    public static <T> FormBuilder<T> create() {
        return new FormBuilder<>();
    }

    public FormBuilder<T> add(String key, String value) {
        if (value != null) form.add(key, value);
        return this;
    }

    public FormBuilder<T> addIfPresent(String key, Object value) {
        if (value != null) form.add(key, value.toString());
        return this;
    }

    public FormBuilder<T> addJson(String key, String template, Object... args) {
        form.add(key, String.format(template, args));
        return this;
    }

    // Optionally, add a method to map fields from DTO
    public FormBuilder<T> mapFromDto(T dto, FieldMapper<T> mapper) {
        mapper.map(this, dto);
        return this;
    }

    public MultiValueMap<String, String> build() {
        return form;
    }

    // Functional interface for custom mapping
    public interface FieldMapper<T> {
        void map(FormBuilder<T> builder, T dto);
    }
}
