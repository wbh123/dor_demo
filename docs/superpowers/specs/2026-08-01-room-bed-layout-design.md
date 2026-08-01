# 逐房间床位布局配置设计

## 1. 目标

允许管理员为每个房间独立配置床位平面位置与朝向，并让学生端Three.js场景读取同一份权威布局。旧房间没有配置时继续使用第一阶段默认布局。

## 2. 数据模型

新增`room_bed_layout`表，每个床位最多一条记录：

```text
bed_id                 床位主键，同时作为布局主键
layout_x               房间局部X坐标
layout_z               房间局部Z坐标
rotation_degrees       0/90/180/270
updated_by             最后修改管理员
version                布局记录版本
created_at/updated_at  时间
```

不在`bed`表增加坐标字段，避免改变第一阶段床位事实。布局记录不存在表示使用默认映射。

## 3. 业务约束

- 一次保存必须包含房间内全部床位，床位不得缺失或重复；
- 坐标限制在房间可视区域：X为`[-5.2, 5.2]`，Z为`[-3.5, 3.5]`；
- 朝向只能为`0、90、180、270`度；
- 同一`bed_frame_id`下的上下铺必须具有完全相同的X、Z和朝向；
- 上下铺的高低由`bed_type`决定，不允许管理员分别设置高度；
- 使用`room.version`执行乐观锁，版本只在前端内存中保存，不显示给用户；
- 保存布局必须填写修改原因；
- 保存成功后递增`room.version`和`room.state_version`并写审计。

## 4. OpenAPI

新增：

```text
GET /api/v1/admin/rooms/{roomId}/bed-layout
PUT /api/v1/admin/rooms/{roomId}/bed-layout
```

读取响应包含房间内部版本、床位类型、床架关系、坐标、朝向和是否为自定义布局。写入请求包含`expectedRoomVersion`、`reason`和全部床位布局项。

## 5. 后端结构

新增`RoomLayoutService`：

- `getLayout(long roomId)`：查询床位与自定义布局，不存在时计算默认布局；
- `updateLayout(long roomId, LayoutCommand command, CurrentUser operator)`：锁定房间、校验版本和布局、批量写入、递增版本并审计；
- 默认布局计算集中在服务内，与前端回退规则保持相同坐标。

`AdminController`只实现生成的`AdminApi`方法并转换命令。`StudentService.room()`将布局字段加入床位快照。

## 6. 管理端交互

宿舍资源列表新增“布局”按钮。布局编辑器采用俯视房间画布：

- 单个上床下桌作为一个拖拽单元；
- 同一床架的上下铺合并为一个拖拽单元，标签同时显示上下铺编码；
- 拖拽按0.25单位吸附；
- 点击旋转按钮按90度循环；
- 移动端同时提供X、Z和朝向输入，避免仅依赖拖拽；
- “恢复默认”只修改当前编辑状态，点击保存后才写入数据库；
- 保存要求填写原因；
- 版本冲突时提示重新加载，不覆盖其他管理员修改。

## 7. 学生端Three.js

`RoomBedScene3D`优先使用床位返回的`layout_x`、`layout_z`和`rotation_degrees`。字段为空时使用第一阶段默认纵向布局。上下铺共享床架模型使用同一布局锚点。

## 8. 错误码

```text
ROOM_LAYOUT_BED_MISMATCH       床位集合缺失、重复或包含其他房间床位
ROOM_LAYOUT_OUT_OF_BOUNDS      坐标超出房间范围
ROOM_LAYOUT_ROTATION_INVALID   朝向非法
ROOM_LAYOUT_BUNK_MISMATCH      同一上下铺床架位置或朝向不一致
ROOM_LAYOUT_VERSION_CONFLICT   房间已被其他管理员修改
ROOM_NOT_FOUND                 房间不存在
```

## 9. 测试

- Flyway V5结构与约束；
- 默认布局读取；
- 自定义布局保存与再次读取；
- 床位缺失、跨房间床位、坐标越界、非法角度、上下铺不一致；
- 乐观锁冲突和审计；
- OpenAPI生成；
- Vue类型检查和生产构建；
- 管理端保存后学生房间接口返回同一布局的真实HTTP流程。
