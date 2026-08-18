package com.wust.dormitory.security;

import java.util.List;
import java.util.Objects;

public record AuthorizationDecision(
        AuthorizationDecisionStatus decision,
        String decisionCode,
        List<AuthorizationDecisionStep> steps) {

    public AuthorizationDecision {
        decision = Objects.requireNonNull(decision, "decision");
        decisionCode = Objects.requireNonNull(decisionCode, "decisionCode");
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public boolean allowed() {
        return decision == AuthorizationDecisionStatus.ALLOWED;
    }
}
