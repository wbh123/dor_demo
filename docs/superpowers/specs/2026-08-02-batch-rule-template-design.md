# 第二阶段批次规则模板设计

## 1. 目标

将批次中的临时占用、续期、组队、随机推荐和未选学生处理规则抽象为可复用、可版本化、可审计的规则模板。新建批次选择精确模板修订，复制批次保留原模板修订和执行快照。

复杂组队邀请、五人队伍、成员移除、主动退队、个人选寝自动退队和邀请失效已经在主分支完成，本设计不重复修改组队流程。

## 2. 核心原则

- 模板修订不可覆盖，只能新增修订；
- 批次继续保存现有规则字段作为执行快照；
- 模板后续变化不得改变已创建批次；
- 同一时刻只有一个默认模板修订；
- 多个非默认模板可以同时启用并供管理员选择；
- 队伍人数规则与现有五人组队一致：允许组队时为2至5人，不允许组队时固定为1人；
- 学生端不展示模板编号、修订号和内部规则版本；
- 所有创建、修订和默认切换必须填写原因并写审计。

## 3. 数据模型

Flyway V10新增`batch_rule_template`，V9已用于学生联系方式与通知，禁止复用版本号。

```text
id
rule_code
rule_name
revision
hold_duration_seconds
hold_renewal_limit
allow_team
team_min_size
team_max_size
allow_student_random
unselected_strategy
rule_version
enabled
is_default
default_marker
created_by
change_reason
version
created_at
updated_at
```

唯一约束：

- `(rule_code, revision)`唯一；
- `default_marker`唯一，保证最多一个默认修订；
- 数值范围与当前批次规则一致；
- `team_max_size`最大为5。

`selection_batch`新增可空外键`rule_template_id`。应用创建的新批次必须写入；可空仅用于兼容历史数据和手工恢复。

迁移规则：

1. 创建系统默认模板；
2. 将已有批次的不同规则组合迁移为禁用的历史模板；
3. 按规则快照为已有批次回填精确模板主键；
4. 不修改已有批次规则字段、队伍、分配或运行状态。

## 4. 执行快照

批次创建时：

```text
选择模板修订
→ 读取模板并校验启用状态
→ 将模板主键写入rule_template_id
→ 将模板规则复制到selection_batch现有字段
```

选寝、临时占用、组队、随机推荐和统一分配继续读取批次快照，不在运行时动态读取模板。

## 5. OpenAPI

新增：

```text
GET  /api/v1/admin/batch-rule-templates
POST /api/v1/admin/batch-rule-templates
POST /api/v1/admin/batch-rule-templates/{templateId}/revisions
```

扩展`BatchRequest`：

```text
ruleTemplateId（可选；旧客户端未提交时使用当前默认模板）
```

模板接口由独立`BatchRuleTemplateController`实现生成的`BatchRuleTemplateApi`，避免继续扩大`AdminController`。

## 6. 后端职责

`BatchRuleTemplateService`负责：

- 列出模板修订；
- 创建新模板；
- 基于已有修订创建新修订；
- 校验临时占用、续期、五人组队和未选策略；
- 乐观锁校验；
- 切换唯一默认模板；
- 为新批次解析显式模板或默认模板；
- 写入模板创建和修订审计。

`AdminService.createBatch`使用服务返回的模板快照创建批次。旧请求中的重复规则字段保留以兼容旧客户端，但模板快照是最终来源。`BatchCopyService`复制`rule_template_id`及现有规则快照。

## 7. 管理端

新增“批次规则”页面：

- 按模板编码分组显示所有修订；
- 默认、启用、停用和历史修订状态清晰区分；
- 可创建新模板；
- 可基于任意修订创建下一修订；
- 表单使用中文字段，不要求管理员编辑JSON；
- 修改原因必填；
- 移动端使用单列表单。

批次创建页面：

- 加载所有启用模板；
- 默认选中默认模板；
- 显示保留时长、续期、组队人数、随机推荐和未选策略摘要；
- 提交精确`ruleTemplateId`；
- 不在同一表单重复编辑规则值。

## 8. 错误处理

- `BATCH_RULE_TEMPLATE_NOT_FOUND`：模板不存在；
- `BATCH_RULE_TEMPLATE_DISABLED`：模板已停用；
- `BATCH_RULE_TEMPLATE_CODE_CONFLICT`：模板编码重复；
- `BATCH_RULE_TEMPLATE_VERSION_CONFLICT`：乐观锁冲突；
- `BATCH_RULE_TEMPLATE_INVALID`：规则值无效；
- `BATCH_RULE_TEMPLATE_DEFAULT_REQUIRED`：没有可用默认模板。

## 9. 测试

- Flyway V10空库和V9升级迁移；
- 历史批次规则快照与模板回填一致；
- 唯一默认约束；
- 创建模板、新修订、五人上限、乐观锁和审计；
- 新建批次使用显式模板；
- 旧客户端不传模板时使用默认模板；
- 模板修订后历史批次规则不变；
- 批次复制保留精确模板引用和快照；
- 管理端页面与移动端表单；
- MySQL、Redis、Spring Boot完整真实接口流程。
