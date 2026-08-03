package io.github.publicdemo.preference;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void reports_an_internal_configuration_error_when_builtin_questionnaire_is_missing() {
        BuiltinQuestionnaireResolver resolver = new BuiltinQuestionnaireResolver(code -> Optional.empty());

        BuiltinQuestionnaireMissingException exception = assertThrows(
                BuiltinQuestionnaireMissingException.class,
                resolver::resolveForNewBatch
        );

        assertEquals("BUILTIN_QUESTIONNAIRE_MISSING", exception.code());
        assertEquals(
                "The built-in preference questionnaire is missing. Run the private database integrity repair.",
                exception.getMessage()
        );
    }

    @Test
    void preserves_existing_batch_binding_unchanged() {
        BuiltinQuestionnaireResolver resolver = new BuiltinQuestionnaireResolver(code -> Optional.empty());
        QuestionnaireRef existing = new QuestionnaireRef(7L, "LEGACY-PREFERENCE-V1");

        QuestionnaireRef preserved = resolver.preserveExistingBinding(existing);

        assertSame(existing, preserved);
    }
}
