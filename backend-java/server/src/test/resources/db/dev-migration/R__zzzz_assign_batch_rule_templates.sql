-- 开发测试数据由重复迁移在V10之后插入批次时，补齐规则模板引用。
-- 编码由规则快照哈希稳定生成，避免后续新增规则时与旧DEV序号冲突。

INSERT INTO batch_rule_template
(rule_code, rule_name, revision,
 hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size,
 allow_student_random, unselected_strategy, rule_version,
 enabled, is_default, created_by, change_reason)
SELECT CONCAT(
           'DEV_',
           UPPER(SUBSTRING(SHA2(CONCAT_WS('|',
               missing_rules.hold_duration_seconds,
               missing_rules.hold_renewal_limit,
               missing_rules.allow_team,
               missing_rules.team_min_size,
               missing_rules.team_max_size,
               missing_rules.allow_student_random,
               missing_rules.unselected_strategy,
               missing_rules.rule_version
           ), 256), 1, 12))
       ),
       CONCAT('开发数据规则 ', missing_rules.rule_version),
       1,
       missing_rules.hold_duration_seconds,
       missing_rules.hold_renewal_limit,
       missing_rules.allow_team,
       missing_rules.team_min_size,
       missing_rules.team_max_size,
       missing_rules.allow_student_random,
       missing_rules.unselected_strategy,
       missing_rules.rule_version,
       0,
       0,
       NULL,
       '开发测试数据规则快照自动回填'
FROM (
    SELECT DISTINCT
           sb.hold_duration_seconds,
           sb.hold_renewal_limit,
           sb.allow_team,
           sb.team_min_size,
           sb.team_max_size,
           sb.allow_student_random,
           sb.unselected_strategy,
           sb.rule_version
    FROM selection_batch sb
    WHERE sb.rule_template_id IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM batch_rule_template template
          WHERE template.hold_duration_seconds = sb.hold_duration_seconds
            AND template.hold_renewal_limit = sb.hold_renewal_limit
            AND template.allow_team = sb.allow_team
            AND template.team_min_size = sb.team_min_size
            AND template.team_max_size = sb.team_max_size
            AND template.allow_student_random = sb.allow_student_random
            AND template.unselected_strategy = sb.unselected_strategy
            AND template.rule_version = sb.rule_version
      )
) missing_rules;

UPDATE selection_batch sb
JOIN batch_rule_template template
  ON template.hold_duration_seconds = sb.hold_duration_seconds
 AND template.hold_renewal_limit = sb.hold_renewal_limit
 AND template.allow_team = sb.allow_team
 AND template.team_min_size = sb.team_min_size
 AND template.team_max_size = sb.team_max_size
 AND template.allow_student_random = sb.allow_student_random
 AND template.unselected_strategy = sb.unselected_strategy
 AND template.rule_version = sb.rule_version
SET sb.rule_template_id = template.id
WHERE sb.rule_template_id IS NULL;
