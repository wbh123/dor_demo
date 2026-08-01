# 第一阶段 OpenAPI 与错误码设计

> 文档编号：DOC-PHASE1-API-001  
> 状态：第一阶段开发基线  
> 日期：2026-08-01

## 1. 契约驱动规则

后端采用与城安智序项目一致的 OpenAPI 驱动模式：

```text
领域 OpenAPI 文件
→ openapi-interface.yaml 汇总
→ Maven OpenAPI Generator
→ 生成 *Api 接口与 DTO
→ Controller 实现生成接口
→ Service 使用内部命令和结果
```

权威入口：

```text
backend-java/model/src/main/resources/openapi-interface.yaml
```

领域文件：

```text
common-response/openapi-common-response.yaml
 auth/openapi-auth.yaml
 admin/openapi-admin.yaml
 admin/openapi-assignment-query.yaml
 admin/openapi-assignment-adjustment.yaml
 student/openapi-student.yaml
 student/openapi-team-release.yaml
 realtime/openapi-realtime.yaml
```

固定要求：

- 路径、HTTP 方法、参数、请求体和响应体只在 OpenAPI 中定义；
- Controller 只使用 `@RestController`，不得手写路由注解；
- Controller 必须实现 `com.wust.dormitory.model.api` 下的生成接口；
- 对外 DTO 必须来自 `com.wust.dormitory.model.dto`；
- 业务服务不得依赖 OpenAPI DTO；
- 前端在开发和构建前通过同一主契约生成 TypeScript 类型；
- 修改契约后必须同时通过 Java 生成编译和前端类型生成构建。

## 2. 生成配置

模型模块使用：

```text
interfaceOnly=true
useTags=true
requestMappingMode=none
skipDefaultInterface=true
```

由标签生成：

```text
AuthApi
AdminApi
StudentApi
RealtimeApi
```

Controller 对应关系：

| 生成接口 | 实现类 |
|---|---|
| `AuthApi` | `AuthController` |
| `AdminApi` | `AdminController` |
| `StudentApi` | `StudentController` |
| `RealtimeApi` | `RealtimeController` |

服务器发送事件在契约中声明为 `text/event-stream`。由于生成器将其映射为字符串响应，运行时实现使用原始 `ResponseEntity` 承载 `SseEmitter`，但路径、参数、媒体类型和权限仍来自 `RealtimeApi`。

## 3. 通用响应

成功响应至少包含：

```json
{
  "success": true,
  "requestId": "请求编号",
  "timestamp": "时间",
  "data": {}
}
```

错误响应至少包含：

```json
{
  "success": false,
  "requestId": "请求编号",
  "timestamp": "时间",
  "data": null,
  "error": {
    "code": "稳定错误码",
    "message": "可读错误信息"
  }
}
```

`X-Request-Id`：

- 客户端可传入；
- 缺失或非法时服务端生成；
- 返回头和响应体中均包含；
- 审计日志保存请求编号。

## 4. 主要接口域

### 4.1 认证

- 管理员和学生登录；
- 学生账号激活；
- 当前用户；
- 退出登录。

### 4.2 管理端

- 工作台统计；
- 专业与学生维护；
- 学生批量导入；
- 楼栋、房间和床位资源；
- 批次创建、准备和状态机；
- 统一分配预演与正式提交；
- 当前分配查询；
- 有原因的人工床位调整；
- 结果导出和审计查询。

### 4.3 学生端

- 学生资料和可参与批次；
- 问卷读取与提交；
- 房间匹配和床位快照；
- 个人床位临时占用、释放和确认；
- 随机推荐；
- 队伍创建、邀请、响应和锁定；
- 队伍多床位整体占用、释放和确认；
- 最终结果查询。

### 4.4 实时推送

- 按批次和房间订阅；
- 连接前执行学生资格、房间性别和批次范围校验；
- 事件包含连接、心跳、床位占用、释放、分配和队伍变化；
- 重连后客户端重新获取 REST 权威快照。

## 5. 错误码分类

### 5.1 认证与权限

| 错误码 | 含义 |
|---|---|
| `UNAUTHORIZED` | 未登录或令牌失效 |
| `FORBIDDEN` | 角色或资源访问权限不足 |
| `AUTH_INVALID` | 用户名或密码错误 |
| `ACTIVATION_INVALID` | 学号与姓名不匹配 |
| `ACCOUNT_ALREADY_ACTIVE` | 学生账号已经激活 |

### 5.2 基础数据

| 错误码 | 含义 |
|---|---|
| `MAJOR_NOT_AVAILABLE` | 专业不存在或已禁用 |
| `STUDENT_NOT_FOUND` | 学生不存在 |
| `ROOM_NOT_FOUND` | 房间不存在或不可用 |
| `ROOM_CAPACITY_MISMATCH` | 容量与启用床位数量不一致 |
| `DATA_CONFLICT` | 唯一约束或引用关系冲突 |

### 5.3 批次和问卷

| 错误码 | 含义 |
|---|---|
| `BATCH_NOT_FOUND` | 批次不存在 |
| `BATCH_NOT_ACCESSIBLE` | 无资格或状态不允许访问 |
| `BATCH_STATUS_INVALID` | 状态机转换非法 |
| `BATCH_NOT_READY` | 发布前资格或宿舍范围不完整 |
| `BATCH_TIME_INVALID` | 开始时间不早于结束时间 |
| `QUESTION_REQUIRED` | 必填问卷题目缺失 |

### 5.4 选寝与并发

| 错误码 | 含义 |
|---|---|
| `BED_NOT_AVAILABLE` | 床位禁用、维护或已分配 |
| `BED_ALREADY_HELD` | 床位已经被临时占用 |
| `BED_OUT_OF_SCOPE` | 床位不属于批次范围 |
| `HOLD_TOKEN_INVALID` | 占用令牌失效或不匹配 |
| `REDIS_UNAVAILABLE` | 临时占用服务不可用 |
| `STUDENT_ALREADY_ASSIGNED` | 学生已经完成最终分配 |
| `ROOM_GENDER_MISMATCH` | 学生与房间性别不一致 |
| `NO_AVAILABLE_BED` | 没有满足硬约束的剩余床位 |

### 5.5 队伍与统一分配

| 错误码 | 含义 |
|---|---|
| `TEAM_DISABLED` | 批次未开启组队 |
| `TEAM_ALREADY_JOINED` | 学生已在有效队伍中 |
| `TEAM_GENDER_MISMATCH` | 队伍成员性别不一致 |
| `TEAM_SIZE_INVALID` | 队伍人数不符合规则 |
| `TEAM_BED_COUNT_MISMATCH` | 床位数量与成员数量不一致 |
| `TEAM_BEDS_INVALID` | 队伍床位不在同一房间或不可用 |
| `ACTIVE_TEAM_NOT_LOCKED` | 统一分配时队伍仍未锁定 |
| `TEAM_ROOM_CAPACITY_INSUFFICIENT` | 没有房间能整体容纳锁定队伍 |

### 5.6 人工调整

| 错误码 | 含义 |
|---|---|
| `ASSIGNMENT_NOT_FOUND` | 当前有效分配不存在 |
| `TARGET_BED_NOT_FOUND` | 目标床位不存在或不在范围 |
| `TARGET_BED_DISABLED` | 目标房间或床位不可用 |
| `TARGET_BED_OCCUPIED` | 目标床位已经分配 |
| `BED_NOT_CHANGED` | 目标床位与当前床位相同 |

## 6. 自动化检查

```bash
python -m unittest scripts/api/test_openapi_contract.py -v
mvn -f backend-java/pom.xml clean verify
cd frontend && npm install && npm run build
```

契约静态测试检查：

- 主契约引用全部可解析；
- `operationId` 全局唯一；
- 四个 Controller 实现生成接口；
- Controller 不含手写路由注解；
- 示例接口和手写公共响应模型已删除；
- 模型模块保持接口生成模式。
