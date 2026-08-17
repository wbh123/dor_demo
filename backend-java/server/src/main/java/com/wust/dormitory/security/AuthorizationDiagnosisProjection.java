package com.wust.dormitory.security;

import java.util.List;
import java.util.Objects;

public record AuthorizationDiagnosisProjection(
        AuthorizationDecisionStatus decision,
        String decisionCode,
        List<AuthorizationDecisionStep> steps) {

    public AuthorizationDiagnosisProjection {
        decision = Objects.requireNonNull(decision, "decision");
        decisionCode = Objects.requireNonNull(decisionCode, "decisionCode");
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static AuthorizationDiagnosisProjection from(AuthorizationDecision decision) {
        Objects.requireNonNull(decision, "decision");
        return new AuthorizationDiagnosisProjection(
                decision.decision(),
                decision.decisionCode(),
                decision.steps());
    }
}
