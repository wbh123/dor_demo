package com.wust.dormitory.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationDecisionTest {

    @Test
    void diagnosisProjectionMustReuseExecutionDecisionWithoutRecomputingIt() {
        AuthorizationDecision executionDecision = new AuthorizationDecision(
                AuthorizationDecisionStatus.DENIED,
                "WRITE_SCOPE_DENIED",
                List.of(
                        new AuthorizationDecisionStep(
                                "ATOMIC_PERMISSION",
                                "原子权限",
                                AuthorizationStepResult.PASS,
                                "room.write",
                                "room.write",
                                "当前岗位拥有所需原子权限",
                                "ADMIN_AUTHORIZATION_PROFILE",
                                "profile:12"),
                        new AuthorizationDecisionStep(
                                "WRITE_SCOPE",
                                "写入数据范围",
                                AuthorizationStepResult.FAIL,
                                "BUILDING:8",
                                "BUILDING:7",
                                "目标资源不在当前岗位写入范围内",
                                "ADMIN_AUTHORIZATION_SCOPE",
                                "room:305")));

        AuthorizationDiagnosisProjection projection = AuthorizationDiagnosisProjection.from(executionDecision);

        assertThat(projection.decision()).isEqualTo(executionDecision.decision());
        assertThat(projection.decisionCode()).isEqualTo(executionDecision.decisionCode());
        assertThat(projection.steps()).containsExactlyElementsOf(executionDecision.steps());
        assertThat(executionDecision.allowed()).isFalse();
    }

    @Test
    void delegationOverrideCanBeExplainedWithoutChangingTheSharedDecisionFact() {
        AuthorizationDecision executionDecision = new AuthorizationDecision(
                AuthorizationDecisionStatus.ALLOWED,
                "RESIDENCY_DELEGATION_ALLOWED",
                List.of(
                        new AuthorizationDecisionStep(
                                "ATOMIC_PERMISSION",
                                "原子权限",
                                AuthorizationStepResult.FAIL,
                                "residency.adjust",
                                "missing",
                                "宿管基础岗位不直接持有正式住宿调整权限",
                                "ADMIN_AUTHORIZATION_PROFILE",
                                "profile:21"),
                        new AuthorizationDecisionStep(
                                "DORM_STAFF_HARD_DENY",
                                "宿管硬拒绝",
                                AuthorizationStepResult.OVERRIDDEN,
                                "deny residency.adjust",
                                "active",
                                "有效临时住宿委派覆盖本次目标操作",
                                "DORM_STAFF_POLICY",
                                null),
                        new AuthorizationDecisionStep(
                                "RESIDENCY_DELEGATION",
                                "临时住宿调整委派",
                                AuthorizationStepResult.PASS,
                                "MOBILE / BUILDING:8",
                                "MOBILE / BUILDING:8",
                                "委派有效且覆盖目标资源",
                                "RESIDENCY_DELEGATION",
                                "delegation:9")));

        AuthorizationDiagnosisProjection projection = AuthorizationDiagnosisProjection.from(executionDecision);

        assertThat(executionDecision.allowed()).isTrue();
        assertThat(projection.decision()).isEqualTo(AuthorizationDecisionStatus.ALLOWED);
        assertThat(projection.steps())
                .extracting(AuthorizationDecisionStep::result)
                .containsExactly(
                        AuthorizationStepResult.FAIL,
                        AuthorizationStepResult.OVERRIDDEN,
                        AuthorizationStepResult.PASS);
    }

    @Test
    void decisionDefensivelyCopiesSteps() {
        AuthorizationDecisionStep step = new AuthorizationDecisionStep(
                "CLIENT_TYPE",
                "客户端限制",
                AuthorizationStepResult.PASS,
                "MOBILE",
                "MOBILE",
                "客户端满足岗位授权约束",
                "AUTHORIZATION_PROFILE",
                null);
        List<AuthorizationDecisionStep> mutable = new ArrayList<>();
        mutable.add(step);

        AuthorizationDecision decision = new AuthorizationDecision(
                AuthorizationDecisionStatus.ALLOWED,
                "ALLOWED",
                mutable);
        mutable.clear();

        assertThat(decision.steps()).containsExactly(step);
        assertThatThrownBy(() -> decision.steps().add(step))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
