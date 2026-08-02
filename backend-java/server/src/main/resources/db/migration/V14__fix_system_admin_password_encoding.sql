-- V14: adapt the seeded SYSTEM_ADMIN BCrypt hash to Spring's DelegatingPasswordEncoder format.
-- V13 may already have been applied, so use a new forward-only migration instead of editing V13.

UPDATE app_user
SET password_hash = CONCAT('{bcrypt}', password_hash),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE user_type = 'SYSTEM_ADMIN'
  AND password_hash LIKE '$2%';
