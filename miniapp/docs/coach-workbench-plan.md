# 小程序「教练员工作台」扩展规划

> 目标：把电脑端代表队（教练员，`UNIT_ADMIN` + `TEAM` 作用域）可操作的全部权限搬到微信小程序。
> 现状：教练员登录（JWT）+ 本队球类比赛 + 首发名单已上线。

## 一、权限面（电脑端代表队可做的事，均只作用于本队）

| 域 | 端点 | 写权限 | 移动端适配度 |
|---|---|---|---|
| 运动员 | `/api/athletes/**` | UNIT_ADMIN 独享 | 核心 |
| 官员 | `/api/officials/**` | UNIT_ADMIN 独享 | 核心 |
| 报名 | `/api/signups/**` + 名单子资源 | SIGNUP_ROLES | 核心 |
| 代表队 | `/api/teams/**` | 本队 | 支撑 |
| 运动会 | `/api/meets/**` | 可创建/编辑 | 只读为主 |
| 照片上传 | `/api/uploads/photo` | UNIT_ADMIN 独享 | 表单配套 |
| 球类首发 | `/api/signups/ball-sports/**` | 已完成 | 已上线 |

桌面专属（不搬/降级）：Excel 导入导出、批量审核。

## 二、API 契约

### 运动员 AthleteRequest
```
category(必), athleteType(必), name(必), gender(必), projects[],
studentNo, idType, idNo, birthDate, bloodType,
emergencyContactType/Name/Phone, athleteIdPhotoUrl, photoUrl, banned, enabled
```
端点：`GET /api/athletes`(本队) · `GET /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}` · `POST /batch-delete` · `PUT /{id}/status`(启停)

### 官员 OfficialRequest
```
teamId(必), personType(必), idType(必), idNo(必), name(必), phone(必),
gender(必), photoUrl(必), nation(必), bloodType, coachLevel, remark
```
端点：`GET /api/officials` · `GET /{id}` · `POST` · `PUT /{id}`（无 DELETE）

### 报名 SignupRequest + 名单
```
创建: meetId(必), eventId(必), signupType(必), teamId, schoolId, teamName,
      captainStudentId, contactTeacherId, remark
流程: POST 创建 → 填名单 → POST /{id}/submit；POST /{id}/withdraw；POST /{id}/captain
运动员名单 PUT /api/signups/{id}/athletes  body={items:[{athleteId, jerseyNo, captain, sortNo, remark}]}
官员名单   PUT /api/signups/{id}/officials  body={items:[{officialId, sortNo, remark}]}
学生名单   POST/DELETE /api/signups/{id}/students
```

### 代表队 / 账号
```
GET /api/teams/{id}(本队) · PUT /{id}(简称/联系人/电话/备注) ·
GET /{id}/account · POST /{id}/reset-password {newPassword}
```

### 照片上传
```
POST /api/uploads/photo  (multipart "file", jpg/png, 50KB–300KB) → "/uploads/official-photos/xxx.jpg"
```

## 三、小程序页面结构

```
pages/coach/
  home/           工作台（已存在）
  lineup/         首发名单（已存在）
  athletes/list/  运动员列表
  athletes/edit/  运动员新增/编辑
  officials/list/ 官员列表
  officials/edit/ 官员新增/编辑
  signup/meets/   可报名运动会列表
  signup/detail/  报名详情（状态/名单/提交撤回）
  signup/roster-athletes/  运动员名单填报
  signup/roster-officials/ 官员名单填报
  team/profile/   本队信息 + 编辑
  team/account/   账号 + 改密码
  me/             我的（球队/退出）
```

复用：`coach-auth.js`（token）、`coach-api.js`（request 封装）。
新增通用组件：名单勾选器（抽自首发名单）、照片选择（chooseImage → uploadPhoto）。

## 四、分阶段

- **Phase 1 运动员管理**：列表（搜索/启停/删除）+ 新增/编辑表单 + 照片上传 + 批量删除。
- **Phase 2 官员管理**：列表 + 新增/编辑表单（personType picker）。
- **Phase 3 报名流程**：运动会列表 → 报名 → 名单填报（运动员/官员）→ 提交/撤回/设队长。
- **Phase 4 代表队 + 我的**：本队信息/编辑、账号改密码、我的页。
- **Phase 5 延后**：运动会只读、Excel 导入、资格审核只读展示。

## 五、后端改动点

绝大多数端点**零改动**（JWT 复用 session 鉴权）。仅需：
1. 回归确认运动员/官员/报名接口的 `teamScopeId()` 本队隔离正确。
2. 照片相对路径前端拼接 `https://sports.jsqyty.com` 前缀。

## 六、决策（已确认）

1. 按 Phase 1→4 顺序推进，每阶段独立可测可发布。
2. 运动会创建暂不搬（复杂表单），只读。
3. Excel 导入暂不搬，手动录入。
4. 报名名单先做运动员+官员，学生名单按需。
