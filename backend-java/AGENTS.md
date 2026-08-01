# 后端开发约束

本文件适用于`backend-java/`下的全部后端开发，并补充根目录`AGENTS.md`。

## 1. OpenAPI驱动模式

后端必须采用与城安智序项目一致的OpenAPI驱动方式：

```text
编写或修改OpenAPI契约
→ Maven生成API接口与数据传输对象
→ Controller实现生成的*Api接口
→ Service使用内部命令、结果或领域对象
```

固定规则：

- 对外接口路径、HTTP方法、参数、请求体和响应体只能在OpenAPI中定义；
- Controller只保留`@RestController`，不得手写`@RequestMapping`、`@GetMapping`、`@PostMapping`等路由注解；
- Controller必须实现`com.wust.dormitory.model.api`下的生成接口；
- 对外请求和响应模型必须来自`com.wust.dormitory.model.dto`；
- Controller中不得重新声明与OpenAPI重复的请求记录、响应记录或枚举；
- OpenAPI DTO只能停留在Controller和转换层，复杂业务服务使用内部命令和结果；
- 通用成功与错误响应由`common-response/openapi-common-response.yaml`生成；
- 认证、管理端、学生端和实时接口分别维护独立契约文件；
- `openapi-interface.yaml`是统一生成入口；
- 修改契约后必须执行模型模块生成与编译，并运行`scripts/api/test_openapi_contract.py`；
- 示例接口、手写公共响应模型和绕过生成接口的Controller不得重新出现。

服务器发送事件接口同样必须进入OpenAPI。由于生成器把`text/event-stream`映射为字符串响应，Controller可以使用原始`ResponseEntity`承载`SseEmitter`，但路径、参数、媒体类型和安全声明必须来自生成的`RealtimeApi`。

## 2. 学生与专业

学生业务信息只包括：

```text
学号
姓名
性别
专业编号
```

实现规则：

- `student`只保存技术主键、`student_number`、`student_name`、`gender`、`major_id`、`created_at`和`updated_at`；
- `major`统一保存`major_code`、`major_name`和启用状态；
- 学生不得重复保存专业编号或专业名称快照；
- 当前不维护班级、年级和学院层级；
- 不得重新引入通用`organization`组织树，除非学校后续明确提出组织管理需求；
- 学生账号关系保存在`app_user.student_id`；
- 学生住宿资格保存在`batch_student_eligibility`，不得作为学生永久字段；
- 学生可选宿舍由批次范围决定，不在学生表保存校区或宿舍范围；
- 学号必须为12位数字且全局唯一；
- 专业编号和专业名称均必须唯一。

## 3. 数据库迁移

- 当前正式迁移版本为V3；
- 已执行的V1、V2、V3不得修改；
- 后续结构调整必须新增Flyway版本迁移；
- 同步更新数据库测试、数据字典、阶段设计和实施记录；
- 第一阶段全部功能验收后才运行数据库固化工具；
- 开发测试数据不得进入生产迁移或最终固化结构SQL。

## 4. 导入与接口

- 学生导入字段只接受学号、姓名、性别和专业编号；
- 专业不存在或已禁用时，学生导入必须失败并返回明确错误；
- 不接受班级、年级、学院、学生校区等当前未定义字段；
- 对外学生接口返回专业编号和必要的专业显示名称，但数据库学生表只保存专业外键；
- 批次资格、问卷、队伍和最终分配通过学生主键关联，不复制学生基础信息。
