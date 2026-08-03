package io.github.publicdemo.preference;

import java.util.Objects;

public final class BuiltinQuestionnaireResolver {
    public static final String BUILTIN_CODE = "SYSTEM-PREFERENCE-V1";

    private final QuestionnaireCatalog catalog;

    public BuiltinQuestionnaireResolver(QuestionnaireCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public QuestionnaireRef resolveForNewBatch() {
        return catalog.findByCode(BUILTIN_CODE)
                .orElseThrow(BuiltinQuestionnaireMissingException::new);
    }

    public QuestionnaireRef preserveExistingBinding(QuestionnaireRef existing) {
        return Objects.requireNonNull(existing, "existing");
    }
}
