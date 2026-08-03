package io.github.publicdemo.preference;

import java.util.Objects;

public record QuestionnaireRef(long id, String code) {
    public QuestionnaireRef {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        code = Objects.requireNonNull(code, "code").trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
