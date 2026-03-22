# 附件三：组织树、赛事系统与学生账号兼容改造后端接口清单

## 一、使用说明

本文档用于输出后端接口层面的落地清单，目标包括：

1. 明确模块划分
2. 明确 URL 设计
3. 明确请求与响应核心字段
4. 明确接口权限边界
5. 为前后端联调提供统一依据

本文档以当前 Spring Boot 项目为基础，接口风格优先延续现有 `/api/**` 结构。

建议新增以下模块：

1. `/api/org/**`
2. `/api/competition/**`
3. `/api/student-account/**` 或继续归并到 `/api/admin/**`
4. `/api/reports/**`

为了减少分散，建议最终使用：

1. `/api/admin/org/**`
2. `/api/admin/competition/**`
3. `/api/admin/reports/**`
4. `/api/teacher/competition/**`
5. `/api/student/competition/**`

## 二、统一响应建议

建议继续使用项目现有 `ApiResponse` 风格。

统一约定：

1. 成功返回 `code = 200`
2. 权限不足返回 `403`
3. 参数校验失败返回 `400`
4. 业务状态不允许返回 `409`

建议对赛事接口增加统一错误码语义：

1. `COMPETITION_NOT_FOUND`
2. `COMPETITION_STATUS_INVALID`
3. `REGISTRATION_SCOPE_DENIED`
4. `REGISTRATION_QUOTA_EXCEEDED`
5. `APPROVAL_STATE_INVALID`
6. `RESULT_ALREADY_PUBLISHED`

## 三、认证与当前用户接口扩展

### 1. 登录返回扩展

#### `POST /api/auth/login`

说明：

1. 保持当前登录方式不变
2. 为管理员增加组织范围字段

建议响应新增字段：

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "token": "jwt",
    "username": "admin01",
    "displayName": "南山区管理员",
    "role": "ORG_ADMIN",
    "schoolId": null,
    "schoolName": null,
    "managedOrgId": 10,
    "managedOrgName": "南山区教育局",
    "managedOrgType": "DISTRICT",
    "forcePasswordChange": false
  }
}
```

### 2. 当前用户信息

#### `GET /api/auth/me`

用途：

1. 前端启动时获取当前角色和组织范围
2. 给菜单渲染提供依据

建议响应字段同登录接口。

## 四、组织树接口

### 1. 查询组织树

#### `GET /api/admin/org/tree`

用途：

1. 查询当前管理员可见的组织树

权限：

1. `SUPER_ADMIN`
2. `ORG_ADMIN`

请求参数：

1. `rootId` 可选
2. `includeDisabled` 可选

示例响应：

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "深圳市教育局",
      "type": "CITY",
      "children": [
        {
          "id": 10,
          "name": "南山区教育局",
          "type": "DISTRICT",
          "children": [
            {
              "id": 100,
              "name": "南山实验学校",
              "type": "SCHOOL"
            }
          ]
        }
      ]
    }
  ]
}
```

### 2. 查询组织节点详情

#### `GET /api/admin/org/{orgId}`

用途：

1. 查看单个节点详情
2. 用于编辑弹窗初始化

### 3. 新增组织节点

#### `POST /api/admin/org`

权限：

1. `SUPER_ADMIN`
2. 有权在本范围内创建下级节点的 `ORG_ADMIN`

请求体建议：

```json
{
  "name": "南山区教育局",
  "code": "SZ_NS",
  "type": "DISTRICT",
  "parentId": 1,
  "contactPhone": "0755-12345678",
  "address": "深圳市南山区"
}
```

### 4. 更新组织节点

#### `PUT /api/admin/org/{orgId}`

说明：

1. 编辑名称、联系人、地址、排序等
2. 不建议轻易允许修改 `type`

### 5. 启用或禁用组织节点

#### `PUT /api/admin/org/{orgId}/status`

请求体建议：

```json
{
  "enabled": true
}
```

### 6. 组织节点统计

#### `GET /api/admin/org/{orgId}/stats`

返回建议：

1. 下级组织数量
2. 学校数量
3. 教师数量
4. 学生数量
5. 学生账号激活率
6. 赛事数量

## 五、学校与管理员接口

### 1. 学校列表

#### `GET /api/admin/schools`

建议扩展支持：

1. `orgId`
2. `orgType`
3. `keyword`
4. `enabled`

### 2. 学校详情

#### `GET /api/admin/schools/{schoolId}`

建议返回新增字段：

1. `orgId`
2. `orgName`
3. `districtOrgId`
4. `districtOrgName`

### 3. 关联学校组织节点

#### `PUT /api/admin/schools/{schoolId}/org-binding`

用途：

1. 给既有学校绑定组织树学校节点

请求体：

```json
{
  "orgId": 100
}
```

### 4. 管理员列表

#### `GET /api/admin/managers`

筛选参数建议：

1. `orgId`
2. `orgType`
3. `keyword`
4. `enabled`

### 5. 新增管理员

#### `POST /api/admin/managers`

请求体建议：

```json
{
  "username": "ns_admin",
  "name": "南山区管理员",
  "password": "***",
  "role": "ORG_ADMIN",
  "managedOrgId": 10,
  "phone": "13800000000"
}
```

### 6. 更新管理员

#### `PUT /api/admin/managers/{teacherId}`

可更新字段：

1. 姓名
2. 手机号
3. `managedOrgId`
4. 启用状态

## 六、学生账号接口扩展

说明：

当前项目已有学生账号管理接口，建议以“增量补充”为主，不推翻现有结构。

### 1. 组织范围统计总览

#### `GET /api/admin/student-accounts/stats`

参数建议：

1. `orgId` 可选
2. `groupBy`，支持 `district`、`school`

权限：

1. `SUPER_ADMIN`
2. `ORG_ADMIN`

响应示例：

```json
{
  "code": 200,
  "data": {
    "summary": {
      "studentCount": 1000,
      "accountCount": 980,
      "activatedCount": 900,
      "lockedCount": 5,
      "disabledCount": 10,
      "activationRate": 0.9
    },
    "items": [
      {
        "orgId": 10,
        "orgName": "南山区教育局",
        "studentCount": 300,
        "activatedCount": 280
      }
    ]
  }
}
```

### 2. 学校维度账号列表

#### `GET /api/admin/student-accounts`

建议权限：

1. 学校管理员可看本校明细
2. 区县、市级默认仅在显式授权下看明细

建议参数：

1. `schoolId`
2. `keyword`
3. `status`
4. `activated`
5. `page`
6. `size`

### 3. 账号操作接口

建议保留现有接口语义，但统一加上组织范围校验：

1. 批量生成账号
2. 批量重置密码
3. 批量启用
4. 批量禁用
5. 导出账号

建议新增统一校验规则：

1. 目标学生必须属于当前用户可管理学校
2. 默认仅学校管理员可执行写操作

## 七、赛事管理接口

### 1. 赛事列表

#### `GET /api/admin/competition`

参数建议：

1. `level`
2. `status`
3. `hostOrgId`
4. `keyword`
5. `page`
6. `size`

权限：

1. `SUPER_ADMIN`
2. `ORG_ADMIN`

数据范围：

1. 仅返回当前组织范围可见赛事

### 2. 创建赛事

#### `POST /api/admin/competition`

请求体建议：

```json
{
  "name": "2026 年深圳市中小学田径运动会",
  "code": "SZ-2026-ATH",
  "level": "CITY",
  "hostOrgId": 1,
  "undertakeOrgId": 10,
  "schoolYear": "2025-2026",
  "term": "SECOND",
  "registrationStartAt": "2026-04-01T00:00:00",
  "registrationEndAt": "2026-04-15T23:59:59",
  "competitionStartAt": "2026-05-01T08:00:00",
  "competitionEndAt": "2026-05-03T18:00:00",
  "description": "全市中小学田径赛事"
}
```

### 3. 查看赛事详情

#### `GET /api/admin/competition/{competitionId}`

建议返回：

1. 基本信息
2. 项目列表
3. 分组列表
4. 名额规则
5. 报名统计
6. 当前状态

### 4. 更新赛事

#### `PUT /api/admin/competition/{competitionId}`

### 5. 赛事状态流转

#### `PUT /api/admin/competition/{competitionId}/status`

请求体建议：

```json
{
  "targetStatus": "REGISTRATION_OPEN"
}
```

应校验：

1. 状态流转是否合法
2. 当前用户是否有发布权限

### 6. 删除赛事

#### `DELETE /api/admin/competition/{competitionId}`

建议限制：

1. 仅 `DRAFT` 状态允许删除
2. 已存在报名数据则禁止删除

## 八、赛事项目与分组接口

### 1. 项目列表

#### `GET /api/admin/competition/{competitionId}/events`

### 2. 新增项目

#### `POST /api/admin/competition/{competitionId}/events`

请求体建议：

```json
{
  "name": "男子100米",
  "eventCode": "M100",
  "genderLimit": "MALE",
  "groupRule": "PRIMARY_HIGH",
  "teamOrIndividual": "INDIVIDUAL",
  "maxEntriesPerSchool": 2,
  "maxEntriesPerDistrict": 10,
  "sortOrder": 10
}
```

### 3. 更新项目

#### `PUT /api/admin/competition/{competitionId}/events/{eventId}`

### 4. 删除项目

#### `DELETE /api/admin/competition/{competitionId}/events/{eventId}`

建议限制：

1. 已有报名时禁止删除，可改为停用

### 5. 分组列表

#### `GET /api/admin/competition/{competitionId}/groups`

### 6. 新增分组

#### `POST /api/admin/competition/{competitionId}/groups`

### 7. 更新分组

#### `PUT /api/admin/competition/{competitionId}/groups/{groupId}`

### 8. 删除分组

#### `DELETE /api/admin/competition/{competitionId}/groups/{groupId}`

## 九、名额规则接口

### 1. 名额规则列表

#### `GET /api/admin/competition/{competitionId}/quota-rules`

### 2. 批量保存名额规则

#### `PUT /api/admin/competition/{competitionId}/quota-rules`

请求体示例：

```json
{
  "rules": [
    {
      "competitionEventId": 1,
      "scopeType": "DISTRICT",
      "scopeOrgId": 10,
      "maxCount": 8,
      "ruleNote": "每区最多 8 人"
    }
  ]
}
```

## 十、报名接口

### 1. 学校查看可报名赛事

#### `GET /api/admin/competition/available-for-school`

权限：

1. 学校管理员
2. 教师

### 2. 创建报名草稿

#### `POST /api/admin/competition/{competitionId}/registrations`

权限：

1. 学校管理员
2. 教师

建议请求体：

```json
{
  "schoolId": 200,
  "remark": "初次报名"
}
```

### 3. 查看本校报名单详情

#### `GET /api/admin/competition/{competitionId}/registrations/{registrationId}`

### 4. 报名单添加队员

#### `POST /api/admin/competition/{competitionId}/registrations/{registrationId}/items`

请求体示例：

```json
{
  "studentId": 3001,
  "competitionEventId": 11,
  "competitionGroupId": 5,
  "teamName": null,
  "roleType": "ATHLETE",
  "seedResult": "12.31"
}
```

### 5. 报名单移除队员

#### `DELETE /api/admin/competition/{competitionId}/registrations/{registrationId}/items/{itemId}`

### 6. 提交报名

#### `PUT /api/admin/competition/{competitionId}/registrations/{registrationId}/submit`

权限：

1. 学校管理员

校验：

1. 报名单属于本校
2. 赛事处于报名期
3. 名额规则未超限
4. 学生与项目条件匹配

### 7. 撤回报名

#### `PUT /api/admin/competition/{competitionId}/registrations/{registrationId}/withdraw`

建议限制：

1. 仅在尚未进入上级审核时允许

## 十一、审核接口

### 1. 待审核报名列表

#### `GET /api/admin/competition/registrations/pending-review`

参数建议：

1. `competitionId`
2. `approvalLevel`
3. `schoolId`
4. `page`
5. `size`

### 2. 查看审核详情

#### `GET /api/admin/competition/registrations/{registrationId}/review-detail`

返回建议：

1. 报名单信息
2. 报名明细
3. 历史审核记录
4. 名额占用情况

### 3. 区县审核

#### `PUT /api/admin/competition/registrations/{registrationId}/district-review`

请求体：

```json
{
  "decision": "APPROVE",
  "comment": "符合要求"
}
```

### 4. 市级终审

#### `PUT /api/admin/competition/registrations/{registrationId}/city-review`

请求体同上。

### 5. 审核记录列表

#### `GET /api/admin/competition/registrations/{registrationId}/approval-records`

## 十二、成绩接口

### 1. 成绩列表

#### `GET /api/admin/competition/{competitionId}/results`

参数建议：

1. `eventId`
2. `groupId`
3. `schoolId`
4. `status`
5. `page`
6. `size`

### 2. 录入成绩

#### `POST /api/admin/competition/{competitionId}/results`

权限：

1. `ORG_ADMIN`
2. `TEACHER`

请求体示例：

```json
{
  "competitionEventId": 11,
  "competitionGroupId": 5,
  "studentId": 3001,
  "resultValue": "12.31",
  "scorePoints": 8.0
}
```

### 3. 批量录入成绩

#### `POST /api/admin/competition/{competitionId}/results/batch`

### 4. 修改成绩

#### `PUT /api/admin/competition/{competitionId}/results/{resultId}`

### 5. 复核成绩

#### `PUT /api/admin/competition/{competitionId}/results/{resultId}/verify`

请求体：

```json
{
  "approved": true,
  "comment": "复核通过"
}
```

### 6. 发布成绩

#### `PUT /api/admin/competition/{competitionId}/results/publish`

说明：

1. 可按赛事整体发布
2. 也可扩展按项目发布

### 7. 导出成绩册

#### `GET /api/admin/competition/{competitionId}/results/export`

## 十三、赛事通知接口

### 1. 通知列表

#### `GET /api/admin/competition/{competitionId}/notices`

### 2. 发布通知

#### `POST /api/admin/competition/{competitionId}/notices`

请求体：

```json
{
  "title": "检录时间调整通知",
  "content": "请各校于 8:00 前完成检录",
  "publishScope": "ALL_VISIBLE"
}
```

### 3. 更新通知

#### `PUT /api/admin/competition/{competitionId}/notices/{noticeId}`

### 4. 删除通知

#### `DELETE /api/admin/competition/{competitionId}/notices/{noticeId}`

## 十四、报表统计接口

### 1. 赛事总览看板

#### `GET /api/admin/reports/competition/dashboard`

返回建议：

1. 赛事数
2. 报名学校数
3. 待审核数
4. 已发布成绩赛事数
5. 参赛学生数

### 2. 学生账号组织统计

#### `GET /api/admin/reports/student-account/overview`

说明：

1. 可按市、区县、学校聚合
2. 为组织治理和赛事准备提供数据基础

### 3. 报名统计报表

#### `GET /api/admin/reports/competition/{competitionId}/registration-summary`

### 4. 成绩统计报表

#### `GET /api/admin/reports/competition/{competitionId}/result-summary`

## 十五、教师端赛事接口

### 1. 教师可见赛事列表

#### `GET /api/teacher/competition`

### 2. 教师查看本校报名草稿

#### `GET /api/teacher/competition/{competitionId}/registrations/current-school`

### 3. 教师新增报名项

#### `POST /api/teacher/competition/{competitionId}/registrations/{registrationId}/items`

### 4. 教师查看成绩录入页数据

#### `GET /api/teacher/competition/{competitionId}/results/entry-data`

### 5. 教师录入成绩

#### `POST /api/teacher/competition/{competitionId}/results`

说明：

1. 和管理端接口可以复用服务层
2. 教师端控制在本校、被授权赛事、被授权项目范围内

## 十六、学生端赛事接口

### 1. 学生查看本人赛事列表

#### `GET /api/student/competition/my`

### 2. 学生查看本人报名详情

#### `GET /api/student/competition/my/{competitionId}`

### 3. 学生查看本人赛事成绩

#### `GET /api/student/competition/my-results`

### 4. 学生查看赛事通知

#### `GET /api/student/competition/notices`

## 十七、服务层建议拆分

建议接口对应的服务至少拆成：

1. `OrganizationService`
2. `OrganizationScopeService`
3. `ManagerAdminService`
4. `CompetitionService`
5. `CompetitionEventService`
6. `CompetitionRegistrationService`
7. `CompetitionApprovalService`
8. `CompetitionResultService`
9. `CompetitionReportService`
10. `StudentAccountScopeStatsService`

## 十八、控制器拆分建议

建议不要做一个超大控制器，建议拆成：

1. `OrgAdminApiController`
2. `ManagerAdminApiController`
3. `CompetitionAdminApiController`
4. `CompetitionRegistrationApiController`
5. `CompetitionApprovalApiController`
6. `CompetitionResultApiController`
7. `CompetitionReportApiController`
8. `TeacherCompetitionApiController`
9. `StudentCompetitionApiController`

## 十九、最小 MVP 接口清单

如果要先做第一版可用系统，建议先实现以下最小集合：

1. `GET /api/admin/org/tree`
2. `POST /api/admin/org`
3. `GET /api/admin/competition`
4. `POST /api/admin/competition`
5. `POST /api/admin/competition/{competitionId}/events`
6. `POST /api/admin/competition/{competitionId}/registrations`
7. `POST /api/admin/competition/{competitionId}/registrations/{registrationId}/items`
8. `PUT /api/admin/competition/{competitionId}/registrations/{registrationId}/submit`
9. `PUT /api/admin/competition/registrations/{registrationId}/district-review`
10. `PUT /api/admin/competition/registrations/{registrationId}/city-review`
11. `GET /api/admin/student-accounts/stats`

## 二十、联调建议

联调阶段建议优先打通以下主链路：

1. 登录获取组织范围
2. 组织树查询
3. 创建赛事
4. 配置项目
5. 学校报名
6. 区县审核
7. 市级终审
8. 查看赛事统计
9. 查看学生账号统计

这样能最快验证“组织树 + 权限范围 + 赛事流程 + 学生账号兼容”这 4 个核心点是否真正闭环。