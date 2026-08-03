package io.github.publicdemo.preference;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuiltinQuestionnaireResolverTest {

    @Test
    void resolves_only_the_fixed_builtin_code_for_new_batches() {
        QuestionnaireCatalog catalog = code -> Optional.ofNullable(Map.of(
                "SYSTEM-PREFERENCE-V1", new QuestionnaireRef(41L, "SYSTEM-PREFERENCE-V1"),
                "OTHER-PREFERENCE-V9", new QuestionnaireRef(99L, "OTHER-PREFERENCE-V9")
        ).get(code));

        BuiltinQuestionnaireResolver resolver = new BuiltinQuestionnaireResolver(catalog);

        QuestionnaireRef result = resolver.resolveForNewBatch();

        assertEquals(41L, result.id());
        assertEquals("SYSTEM-PREFERENCE-V1", result.code());
    }

    @Test
    void queries_the_catalog_once_with_the_exact_builtin_code() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> requestedCode = new AtomicReference<>();
        QuestionnaireCatalog catalog = code -> {
            calls.incrementAndGet();
            requestedCode.set(code);
            return Optional.of(new QuestionnaireRef(41L, code));
        };

        QuestionnaireRef result = new BuiltinQuestionnaireResolver(catalog).resolveForNewBatch();

        assertEquals("SYSTEM-PREFERENCE-V1", requestedCode.get());
        assertEquals(1, calls.get());
        assertEquals("SYSTEM-PREFERENCE-V1", result.code());
    }

    @Test
    void reports_a_stable_public_configuration_error_when_builtin_questionnaire_is_missing() {
        BuiltinQuestionnaireResolver resolver = new BuiltinQuestionnaireResolver(code -> Optional.empty());

        BuiltinQuestionnaireMissingException exception = assertThrows(
                BuiltinQuestionnaireMissingException.class,
                resolver::resolveForNewBatch
        );

        assertEquals("BUILTIN_QUESTIONNAIRE_MISSING", exception.code());
        assertEquals("The built-in preference questionnaire is unavailable.", exception.getMessage());
        assertFalse(exception.getMessage().toLowerCase().contains("private"));
        assertFalse(exception.getMessage().toLowerCase().contains("database integrity"));
    }

    @Test
    void preserves_existing_batch_binding_unchanged_even_when_builtin_catalog_is_unavailable() {
        BuiltinQuestionnaireResolver resolver = new BuiltinQuestionnaireResolver(code -> {
            throw new AssertionError("historical binding must not query the current catalog");
        });
        QuestionnaireRef existing = new QuestionnaireRef(7L, "LEGACY-PREFERENCE-V1");

        QuestionnaireRef preserved = resolver.preserveExistingBinding(existing);

        assertSame(existing, preserved);
    }

    @Test
    void rejects_null_dependencies_and_null_historical_bindings() {
        assertThrows(NullPointerException.class, () -> new BuiltinQuestionnaireResolver(null));
        BuiltinQuestionnaireResolver resolver = new BuiltinQuestionnaireResolver(code -> Optional.empty());
        assertThrows(NullPointerException.class, () -> resolver.preserveExistingBinding(null));
    }

    @Test
    void questionnaire_reference_normalizes_code_and_rejects_invalid_values() {
        QuestionnaireRef normalized = new QuestionnaireRef(1L, "  SYSTEM-PREFERENCE-V1  ");
        assertEquals("SYSTEM-PREFERENCE-V1", normalized.code());

        assertThrows(IllegalArgumentException.class, () -> new QuestionnaireRef(0L, "SYSTEM-PREFERENCE-V1"));
        assertThrows(IllegalArgumentException.class, () -> new QuestionnaireRef(-1L, "SYSTEM-PREFERENCE-V1"));
        assertThrows(NullPointerException.class, () -> new QuestionnaireRef(1L, null));
        assertThrows(IllegalArgumentException.class, () -> new QuestionnaireRef(1L, "   "));
    }
}
