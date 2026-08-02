-- V11：规范化V9之前可能遗留的欢迎语JSON标量或不完整对象。
-- V1至V10均保持不可变，本迁移只修复已有数据。

UPDATE system_setting
SET setting_value = JSON_OBJECT(
        'zh-CN',
        CASE
            WHEN JSON_TYPE(setting_value) = 'OBJECT'
                 AND COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."zh-CN"')), '') = 'STRING'
                 AND LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."zh-CN"')))) > 0
                THEN LEFT(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."zh-CN"'))), 700)
            WHEN JSON_TYPE(setting_value) = 'STRING'
                 AND LENGTH(TRIM(JSON_UNQUOTE(setting_value))) > 0
                THEN LEFT(TRIM(JSON_UNQUOTE(setting_value)), 700)
            ELSE '欢迎使用武汉科技大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍与床位。'
        END,
        'en-US',
        CASE
            WHEN JSON_TYPE(setting_value) = 'OBJECT'
                 AND COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."en-US"')), '') = 'STRING'
                 AND LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."en-US"')))) > 0
                THEN LEFT(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."en-US"'))), 220)
            ELSE 'Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room and bed.'
        END
    ),
    version = version + 1
WHERE setting_key = 'STUDENT_WELCOME_MESSAGE'
  AND (
      JSON_TYPE(setting_value) <> 'OBJECT'
      OR COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."zh-CN"')), '') <> 'STRING'
      OR COALESCE(LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."zh-CN"')))), 0) = 0
      OR COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."en-US"')), '') <> 'STRING'
      OR COALESCE(LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."en-US"')))), 0) = 0
  );
