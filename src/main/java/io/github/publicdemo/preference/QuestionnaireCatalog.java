package io.github.publicdemo.preference;

import java.util.Optional;

@FunctionalInterface
public interface QuestionnaireCatalog {
    Optional<QuestionnaireRef> findByCode(String code);
}
