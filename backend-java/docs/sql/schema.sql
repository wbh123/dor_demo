-- ============================================================
-- 武汉科技大学学生宿舍智能选择系统
-- Schema version: V1-V18
-- 数据库架构安装入口：Flyway V1～V18
--
-- 生成方式：python scripts/db/build_frozen_baseline.py
-- 正式 Flyway 版本迁移是数据库结构的唯一事实来源。
-- 本文件使用 MySQL 客户端 SOURCE 命令按版本顺序执行全部正式迁移。
-- Navicat 请使用 backend-java/docs/sql/navicat 目录中的脚本。
-- ============================================================

SET NAMES utf8mb4;

-- V1: V1__create_phase1_schema.sql
SOURCE backend-java/server/src/main/resources/db/migration/V1__create_phase1_schema.sql

-- V2: V2__enforce_fixed_room_gender.sql
SOURCE backend-java/server/src/main/resources/db/migration/V2__enforce_fixed_room_gender.sql

-- V3: V3__normalize_major_and_minimize_student.sql
SOURCE backend-java/server/src/main/resources/db/migration/V3__normalize_major_and_minimize_student.sql

-- V4: V4__refine_questionnaire_and_active_batch_rules.sql
SOURCE backend-java/server/src/main/resources/db/migration/V4__refine_questionnaire_and_active_batch_rules.sql

-- V5: V5__add_room_bed_layout.sql
SOURCE backend-java/server/src/main/resources/db/migration/V5__add_room_bed_layout.sql

-- V6: V6__version_matching_weight_schemes.sql
SOURCE backend-java/server/src/main/resources/db/migration/V6__version_matching_weight_schemes.sql

-- V7: V7__add_student_welcome_settings.sql
SOURCE backend-java/server/src/main/resources/db/migration/V7__add_student_welcome_settings.sql

-- V8: V8__expand_personal_preferences.sql
SOURCE backend-java/server/src/main/resources/db/migration/V8__expand_personal_preferences.sql

-- V9: V9__add_student_contact_and_notifications.sql
SOURCE backend-java/server/src/main/resources/db/migration/V9__add_student_contact_and_notifications.sql

-- V10: V10__add_batch_rule_templates.sql
SOURCE backend-java/server/src/main/resources/db/migration/V10__add_batch_rule_templates.sql

-- V11: V11__harden_welcome_message_json.sql
SOURCE backend-java/server/src/main/resources/db/migration/V11__harden_welcome_message_json.sql

-- V12: V12__add_single_client_subscription_entitlements.sql
SOURCE backend-java/server/src/main/resources/db/migration/V12__add_single_client_subscription_entitlements.sql

-- V13: V13__seed_single_client_subscription_catalog.sql
SOURCE backend-java/server/src/main/resources/db/migration/V13__seed_single_client_subscription_catalog.sql

-- V14: V14__fix_system_admin_password_encoding.sql
SOURCE backend-java/server/src/main/resources/db/migration/V14__fix_system_admin_password_encoding.sql

-- V15: V15__add_batch_selection_modes.sql
SOURCE backend-java/server/src/main/resources/db/migration/V15__add_batch_selection_modes.sql

-- V16: V16__add_residency_student_category_and_transfer_support.sql
SOURCE backend-java/server/src/main/resources/db/migration/V16__add_residency_student_category_and_transfer_support.sql

-- V17: V17__restore_required_system_configuration.sql
SOURCE backend-java/server/src/main/resources/db/migration/V17__restore_required_system_configuration.sql

-- V18: V18__seed_required_business_reference_data.sql
SOURCE backend-java/server/src/main/resources/db/migration/V18__seed_required_business_reference_data.sql
