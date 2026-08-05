package com.wust.dormitory.subscription;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class FeatureRouteCatalog {
    private final List<RouteRule> rules = List.of(
            rule("/api/v1/admin/waitlist/entries/", "/assign", FeatureCodes.P3_WAITLIST_ASSIGN, AccessMode.START_NEW),
            rule("/api/v1/admin/waitlist/entries/", null, FeatureCodes.P3_WAITLIST_MANAGE, AccessMode.START_NEW),
            rule("/api/v1/admin/waitlist/settings", null, FeatureCodes.P3_WAITLIST_MANAGE, AccessMode.START_NEW),
            rule("/api/v1/admin/waitlist/scan", null, FeatureCodes.P3_WAITLIST_MANAGE, AccessMode.START_NEW),
            rule("/api/v1/admin/waitlist/entries", null, FeatureCodes.P3_WAITLIST_HISTORY, AccessMode.READ_EXISTING),
            rule("/api/v1/student/waitlist", null, FeatureCodes.P3_WAITLIST_REQUEST, AccessMode.START_NEW),
            rule("/api/v1/admin/room-exchanges/", "/approve", FeatureCodes.P3_ROOM_EXCHANGE_EXECUTE, AccessMode.START_NEW),
            rule("/api/v1/admin/room-exchanges/", "/reject", FeatureCodes.P3_ROOM_EXCHANGE_REVIEW, AccessMode.START_NEW),
            rule("/api/v1/admin/room-exchanges/settings", null, FeatureCodes.P3_ROOM_EXCHANGE_REVIEW, AccessMode.START_NEW),
            rule("/api/v1/admin/room-exchanges", null, FeatureCodes.P3_ROOM_EXCHANGE_HISTORY, AccessMode.READ_EXISTING),
            rule("/api/v1/student/room-exchanges", null, FeatureCodes.P3_ROOM_EXCHANGE_REQUEST, AccessMode.START_NEW),
            rule("/api/v1/admin/room-change/requests/", "/approve", FeatureCodes.P3_ROOM_CHANGE_APPROVE, AccessMode.START_NEW),
            rule("/api/v1/admin/room-change/requests/", "/reject", FeatureCodes.P3_ROOM_CHANGE_REJECT, AccessMode.START_NEW),
            rule("/api/v1/admin/room-change/settings", null, FeatureCodes.P3_ROOM_CHANGE_REVIEW, AccessMode.START_NEW),
            rule("/api/v1/admin/room-change/requests", null, FeatureCodes.P3_ROOM_CHANGE_REVIEW, AccessMode.READ_EXISTING),
            rule("/api/v1/student/room-change", null, FeatureCodes.P3_ROOM_CHANGE_REQUEST, AccessMode.START_NEW),
            rule("/api/v1/admin/operations/overview", null, FeatureCodes.P2_OPERATION_STATISTICS, AccessMode.READ_EXISTING),
            rule("/api/v1/admin/operations/health", null, FeatureCodes.P2_OPERATION_HEALTH_VIEW, AccessMode.READ_EXISTING),
            rule("/api/v1/admin/batches/", "/allocation/optimized-preview", FeatureCodes.P2_FAIRNESS_METRIC_VIEW, AccessMode.READ_EXISTING),
            new RouteRule("/api/v1/admin/rooms/", "/bed-layout", FeatureCodes.P2_ROOM_LAYOUT_UPDATE, AccessMode.START_NEW, HttpMethod.PUT),
            new RouteRule("/api/v1/admin/rooms/", "/bed-layout", FeatureCodes.P2_ROOM_LAYOUT_VIEW, AccessMode.READ_EXISTING, HttpMethod.GET),
            rule("/api/v1/admin/rooms/", "/beds", FeatureCodes.P2_BED_TYPE_UPDATE, AccessMode.START_NEW),
            rule("/api/v1/admin/matching-weight", null, FeatureCodes.P2_MATCHING_SCHEME_REVISE, AccessMode.START_NEW),
            rule("/api/v1/admin/batches/", "/copy", FeatureCodes.P2_BATCH_COPY, AccessMode.START_NEW),
            rule("/api/v1/admin/batch-rule-templates", null, FeatureCodes.P2_RULE_TEMPLATE_REVISE, AccessMode.START_NEW),
            rule("/api/v1/admin/settings/student-welcome", null, FeatureCodes.P2_WELCOME_MESSAGE_UPDATE, AccessMode.START_NEW),
            rule("/api/v1/student/preferences/insight", null, FeatureCodes.P2_STUDENT_PROFILE_INSIGHT, AccessMode.READ_EXISTING),
            rule("/api/v1/student/batches/", "/random-recommendation", FeatureCodes.P2_ROOM_RECOMMENDATION, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/notifications", null, FeatureCodes.P2_STUDENT_NOTIFICATION_VIEW, AccessMode.READ_EXISTING),
            rule("/api/v1/student/profile/phone", null, FeatureCodes.P2_STUDENT_CONTACT_SELF_UPDATE, AccessMode.START_NEW),
            rule("/api/v1/student/team/members/", null, FeatureCodes.P2_TEAM_MEMBER_REMOVE, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/team/leave", null, FeatureCodes.P2_TEAM_MEMBER_LEAVE, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/team/invitations/", null, FeatureCodes.P2_TEAM_INVITATION_CANCEL, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/three-dimensional", null, FeatureCodes.P2_THREE_DIMENSIONAL_SELECTION, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/admin/students", null, FeatureCodes.P1_IDENTITY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/majors", null, FeatureCodes.P1_IDENTITY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/import", null, FeatureCodes.P1_IDENTITY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/campuses", null, FeatureCodes.P1_DORMITORY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/buildings", null, FeatureCodes.P1_DORMITORY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/floors", null, FeatureCodes.P1_DORMITORY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/rooms", null, FeatureCodes.P1_DORMITORY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/beds", null, FeatureCodes.P1_DORMITORY_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/batches", null, FeatureCodes.P1_BATCH_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/questionnaires", null, FeatureCodes.P1_PREFERENCE_BASIC, AccessMode.START_NEW),
            rule("/api/v1/admin/allocation", null, FeatureCodes.P1_UNIFIED_ALLOCATION, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/admin/assignments", null, FeatureCodes.P1_ASSIGNMENT_MANAGEMENT, AccessMode.READ_EXISTING),
            rule("/api/v1/admin/audit", null, FeatureCodes.P1_BASIC_EXPORT_AUDIT, AccessMode.READ_EXISTING),
            rule("/api/v1/admin/export", null, FeatureCodes.P1_BASIC_EXPORT_AUDIT, AccessMode.READ_EXISTING),
            rule("/api/v1/student/preferences", null, FeatureCodes.P1_PREFERENCE_BASIC, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/recommend", null, FeatureCodes.P1_RANDOM_RECOMMENDATION, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/team", null, FeatureCodes.P1_TEAM_SELECTION, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/selection", null, FeatureCodes.P1_SELF_SELECTION, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/holds", null, FeatureCodes.P1_SELF_SELECTION, AccessMode.CONTINUE_EXISTING_BATCH),
            rule("/api/v1/student/assignment", null, FeatureCodes.P1_ASSIGNMENT_MANAGEMENT, AccessMode.READ_EXISTING),
            rule("/api/v1/realtime", null, FeatureCodes.P1_REALTIME_STATUS, AccessMode.CONTINUE_EXISTING_BATCH)
    );

    public Optional<RouteRule> resolve(String method, String uri) { return rules.stream().filter(rule -> rule.matches(method, uri)).findFirst(); }
    public List<RouteRule> all() { return rules; }
    private static RouteRule rule(String prefix,String contains,String featureCode,AccessMode mode){return new RouteRule(prefix,contains,featureCode,mode,null);}
    public record RouteRule(String prefix,String contains,String featureCode,AccessMode accessMode,HttpMethod method){boolean matches(String requestMethod,String uri){return uri.startsWith(prefix)&&(contains==null||uri.contains(contains))&&(method==null||method.matches(requestMethod));}}
}
