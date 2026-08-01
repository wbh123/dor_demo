-- ============================================================
-- V4：优化问卷吸烟偏好，并约束学生只能参加一个活动批次
-- ============================================================

-- 1. “是否接受室友吸烟”由布尔题调整为三态单选题。
UPDATE questionnaire_question
SET question_text = '是否接受室友吸烟？',
    question_type = 'SINGLE_CHOICE',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE question_code = 'SMOKING_ACCEPTANCE';

DELETE qo
FROM questionnaire_option qo
JOIN questionnaire_question qq ON qq.id = qo.question_id
WHERE qq.question_code = 'SMOKING_ACCEPTANCE';

INSERT INTO questionnaire_option
(question_id, option_code, option_text, feature_value, sort_order, enabled)
SELECT id, 'ACCEPT', '接受', 1.0000, 1, 1
FROM questionnaire_question
WHERE question_code = 'SMOKING_ACCEPTANCE'
UNION ALL
SELECT id, 'REJECT', '不接受', 0.0000, 2, 1
FROM questionnaire_question
WHERE question_code = 'SMOKING_ACCEPTANCE'
UNION ALL
SELECT id, 'ANY', '均可', 0.5000, 3, 1
FROM questionnaire_question
WHERE question_code = 'SMOKING_ACCEPTANCE';

-- 将历史布尔答案转换为三态字符串。
UPDATE questionnaire_answer qa
JOIN questionnaire_question qq ON qq.id = qa.question_id
SET qa.answer_json = CASE
    WHEN JSON_UNQUOTE(qa.answer_json) = 'true' THEN JSON_QUOTE('ACCEPT')
    WHEN JSON_UNQUOTE(qa.answer_json) = 'false' THEN JSON_QUOTE('REJECT')
    ELSE JSON_QUOTE('ANY')
END,
qa.version = qa.version + 1
WHERE qq.question_code = 'SMOKING_ACCEPTANCE';

UPDATE student_feature
SET feature_vector_json = JSON_SET(
    feature_vector_json,
    '$.smokingAcceptance',
    CASE JSON_UNQUOTE(JSON_EXTRACT(feature_vector_json, '$.smokingAcceptance'))
        WHEN 'true' THEN 'ACCEPT'
        WHEN 'false' THEN 'REJECT'
        WHEN 'ACCEPT' THEN 'ACCEPT'
        WHEN 'REJECT' THEN 'REJECT'
        ELSE 'ANY'
    END
),
calculated_at = CURRENT_TIMESTAMP(3)
WHERE JSON_CONTAINS_PATH(feature_vector_json, 'one', '$.smokingAcceptance');

-- 2. 以最小锁表表达“同一学生同一时刻只能属于一个活动批次”。
-- 活动批次包括：已发布、选寝中、暂停。
-- 锁表由Spring事务服务维护，不依赖需要数据库高权限的触发器。
CREATE TABLE active_batch_student_lock (
    student_id BIGINT NOT NULL COMMENT '学生主键，同一学生只能出现一次',
    batch_id BIGINT NOT NULL COMMENT '当前活动批次主键',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (student_id),
    UNIQUE KEY uk_active_batch_student_batch (batch_id, student_id),
    CONSTRAINT fk_active_batch_lock_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT fk_active_batch_lock_batch
        FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='活动选寝批次学生唯一锁';

-- 若升级时已经存在活动批次，先建立唯一锁；存在冲突时迁移会因主键冲突而失败。
INSERT INTO active_batch_student_lock (student_id, batch_id)
SELECT e.student_id, e.batch_id
FROM batch_student_eligibility e
JOIN selection_batch sb ON sb.id = e.batch_id
WHERE e.eligibility_status = 'ELIGIBLE'
  AND sb.batch_status IN ('PUBLISHED', 'OPEN', 'PAUSED');
