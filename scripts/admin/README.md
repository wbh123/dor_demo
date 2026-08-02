# 系统管理员本地运维脚本

## 重置密码

系统只允许一个 `SYSTEM_ADMIN`。忘记密码时，在项目根目录执行：

```bash
python -m pip install bcrypt pymysql redis
python scripts/admin/reset_system_admin_password.py
```

脚本会隐藏输入和确认新密码。也可以通过参数传入，但不建议写入 shell 历史：

```bash
python scripts/admin/reset_system_admin_password.py --password '新的高强度密码'
```

密码至少 12 位，并同时包含：

- 大写字母；
- 小写字母；
- 数字；
- 特殊字符。

数据库和 Redis 连接读取：

```text
MYSQL_HOST
MYSQL_PORT
MYSQL_USER
MYSQL_PASSWORD
MYSQL_DATABASE
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
REDIS_DATABASE
AUTH_TOKEN_PREFIX
```

重置后：

1. 数据库只保存 BCrypt 哈希；
2. `password_change_required` 被设为 `1`；
3. 能识别出的系统管理员 Redis 令牌被删除；
4. 写入 `SYSTEM_ADMIN_PASSWORD_RESET` 平台审计；
5. 下次登录必须再次修改密码。

脚本不会打印新密码。若 Redis 序列化格式或令牌前缀与默认值不同，请在本地验收时依据 `AuthTokenService` 调整 `AUTH_TOKEN_PREFIX` 和令牌解析逻辑。
