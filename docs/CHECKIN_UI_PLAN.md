# Check-in Web UI 实施计划

## 当前前提

后端已经提供：

```text
GET  /v1/me
GET  /v1/catalog?kind=country|admin1&q=&parent_id=
GET  /v1/checkins
POST /v1/checkins
GET  /v1/summary
```

`web/src/api.ts` 已经封装了这些请求。当前 `web/src/main.ts` 仍是登录后欢迎页，下一步只改 Web UI，不改后端数据模型。

## UI 目标

完成一个最小闭环：

```text
登录
  ↓
选择 Country 或 Admin 1
  ↓
搜索并选择地点
  ↓
填写访问日期和备注
  ↓
Check in
  ↓
看到统计和最近记录
```

## 页面结构

### 未登录状态

保留当前登录页：

- Email
- Password
- Log in
- Create account
- Firebase 未配置时显示配置提示

### 登录后 Dashboard

页面分成四部分：

```text
Header
  Logo / 页面标题
  当前用户 email
  Log out

Summary cards
  Total check-ins
  Unique unlocks
  Countries
  Admin 1 regions

New check-in card
  Dimension selector
  Country parent selector（仅 Admin 1 显示）
  Search input
  Search results
  Selected entity
  Visited date
  Note
  Check in button

Recent check-ins card
  地点名称
  类型
  访问日期
  备注
```

## 交互状态

前端需要明确管理以下状态：

```text
authUser              Firebase 当前用户
selectedDimension     country | admin1
selectedParent        Admin 1 的父国家
searchQuery           搜索关键词
catalogResults        当前搜索结果
selectedEntity        当前选中的 catalog entity
visitedDate           访问日期
note                  备注
summary               统计结果
checkins              最近记录
loading               初次加载/搜索/提交状态
error                 API 或表单错误
success               check-in 成功提示
```

## 交互流程

### 初次加载

1. Firebase `onAuthStateChanged` 确认用户已登录。
2. 并行请求 `/v1/me`、`/v1/summary`、`/v1/checkins`。
3. 加载默认 Country 搜索结果或显示提示。
4. Admin 1 的国家 selector 请求全部国家，使用较大的 limit；之后的行政区搜索必须带 `parent_id`。

### 搜索 catalog

- Country：调用 `/v1/catalog?kind=country&q=<query>`。
- Admin 1：必须先选国家，然后调用 `/v1/catalog?kind=admin1&parent_id=<countryId>&q=<query>`。
- 搜索结果最多显示 25 条。
- 不把 4,117 条数据一次性放进浏览器。
- 搜索结果为空时显示明确空状态。

### 选择地点

- 点击结果后保存 `selectedEntity`。
- 显示地点名称、类型和 code。
- 选择新搜索结果前允许取消当前选择。
- 没有选择地点时，提交按钮禁用或提交时显示错误。

### 提交 check-in

请求：

```json
{
  "entity_id": "admin1:US-CA",
  "visited_at": "2025-01-01T00:00:00Z",
  "note": "Optional note"
}
```

提交期间：

- 禁用按钮，显示 `Saving...`
- 防止重复点击
- 成功后刷新 summary 和 check-in list
- 清空 selected entity、note
- 保留当前 dimension 和 parent country
- 失败时保留表单内容并显示错误

## 视觉和响应式要求

- 使用现有暖色主题，不引入 UI framework。
- Desktop 使用两列布局：左侧新增打卡，右侧最近记录。
- Mobile 变成单列布局。
- Summary cards 在小屏幕上自动换行。
- 重点按钮使用明确的 `Check in` 文案。
- 所有加载、空列表、错误和成功状态都要有可见反馈。

## 实施拆分

### UI-1：Dashboard shell

- 替换登录后的欢迎页。
- 加入 Header、Summary cards、Check-in card、Recent list 容器。
- 保留 logout 和 `/v1/me` 验证。

### UI-2：Catalog search

- 加入 Country/Admin 1 selector。
- 加入 parent country selector。
- 加入搜索输入、结果列表和选择状态。
- 只调用已有 catalog API。

### UI-3：Submit check-in

- 加入日期、备注和提交表单。
- 调用 `POST /v1/checkins`。
- 增加 loading、error、success 状态。

### UI-4：Summary and history

- 调用 `/v1/summary` 和 `/v1/checkins`。
- 显示数量和最近记录。
- 提交成功后刷新数据。

### UI-5：验证和提交

- `npm run build`
- 使用 fake API 或临时后端做页面 smoke test。
- 配置真实 Firebase 后完成手动 E2E：登录 → 搜索 California → check-in → 查看列表和统计。
- 将 UI 文件作为独立 commit push。

## 本阶段不做

- 地图
- GPS 权限
- 图片上传
- 编辑/删除
- 离线队列
- 社交分享和排名
- 复杂成就规则

## 验收标准

- 登录用户能看到 Dashboard。
- 能搜索并选择一个国家。
- 能选择国家后搜索一级行政区。
- 能提交访问日期和备注。
- 提交后列表出现新记录。
- Summary 显示总打卡数和唯一解锁数。
- 对同一地点重复打卡时，总打卡数增加但唯一解锁数不增加。
- 页面在桌面和手机宽度下都可用。
