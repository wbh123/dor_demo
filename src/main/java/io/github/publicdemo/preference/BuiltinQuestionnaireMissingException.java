package io.github.publicdemo.preference;

public final class BuiltinQuestionnaireMissingException extends RuntimeException {
    private static final String ERROR_CODE = "BUILTIN_QUESTIONNAIRE_MISSING";

    public BuiltinQuestionnaireMissingException() {
        super("The built-in preference questionnaire is missing. Run the private database integrity repair.");
    }

    public String code() {
        return ERROR_CODE;
    }
}
