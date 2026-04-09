# 组织树与赛事系统开发前决策冻结稿

## 一、文档定位

本文档是在开发前决策清单基础上形成的默认冻结稿，用于给第一阶段开发提供统一口径。

适用原则：

1. 本文档优先服务于第一阶段底座开发和赛事 MVP 开发
2. 未在本文档中冻结的内容，默认不进入第一阶段正式实现
3. 后续若口径变更，应先更新本文件，再修改 SQL 草案、权限矩阵和接口清单

关联文档：

1. [competition-org-tree-pre-dev-decision-checklist.md](/C:/Users/28970/Desktop/code/PE_TEACHER_ASSISTANT_JAVA/competition-org-tree-pre-dev-decision-checklist.md)
2. [competition-org-tree-implementation-manual.md](/C:/Users/28970/Desktop/code/PE_TEACHER_ASSISTANT_JAVA/competition-org-tree-implementation-manual.md)
3. [competition-org-tree-sql-draft.md](/C:/Users/28970/Desktop/code/PE_TEACHER_ASSISTANT_JAVA/competition-org-tree-sql-draft.md)
4. [competition-org-tree-permission-matrix.md](/C:/Users/28970/Desktop/code/PE_TEACHER_ASSISTANT_JAVA/competition-org-tree-permission-matrix.md)
5. [competition-org-tree-api-design.md](/C:/Users/28970/Desktop/code/PE_TEACHER_ASSISTANT_JAVA/competition-org-tree-api-design.md)

## 二、冻结结论总览

| 序号 | 决策项 | 冻结结论 |
|---|---|---|
| 1 | 区县和学校的真实组织数据来源 | 以 `schools` 为学校主数据，额外准备“学校 -> 区县”映射表回填组织树 |
| 2 | 角色兼容策略最终定稿 | 第一阶段兼容 `ADMIN` 与 `ORG_ADMIN`，新上级管理员统一使用 `ORG_ADMIN` |
| 3 | 赛事级别边界 | 支持市级、区县级、校级三类赛事，按组织层级创建和审核 |
| 4 | 报名审核状态机 | 采用 `DRAFT / SUBMITTED / DISTRICT_APPROVED / DISTRICT_REJECTED / CITY_APPROVED / CITY_REJECTED / WITHDRAWN` |
| 5 | 成绩模型口径 | 第一阶段采用 `result_value` 字符串 + `rank_no` + `score_points` 的简化模型 |
| 6 | 上级对学生账号的权限边界 | 上级默认只看统计，不默认操作学校学生账号明细 |
| 7 | Web 和移动端第一阶段范围 | 第一阶段优先 Web 管理端，教师端只保留最小补充空间，移动端赛事能力后置 |
| 8 | 上线迁移方式 | 采用增量结构 + 灰度开放 + 测试账号先行 |

## 三、逐项冻结口径

## 1. 组织数据来源

冻结结论：

1. `schools` 仍然是学校主数据来源
2. 第一阶段不重做学校主数据表
3. 额外准备“学校 -> 区县”映射数据作为组织树初始化输入
4. 市级组织先默认只有一个顶层节点
5. 每个学校对应一个 `Organization(type=SCHOOL)` 节点

补充说明：

1. 第一阶段允许先手工维护映射表
2. 如后续需要，再补学校表内的区县归属字段

## 2. 角色兼容策略

冻结结论：

1. 第一阶段保留旧角色 `ADMIN`
2. 第一阶段新增市级和区县管理员统一使用 `ORG_ADMIN`
3. 新代码中的权限判断应逐步兼容 `ADMIN` 和 `ORG_ADMIN`
4. 学校管理员在兼容期内仍可继续使用 `ADMIN`
5. 不在第一阶段强制批量迁移历史 `ADMIN`

补充说明：

1. 第二阶段或治理收口阶段，再统一处理 `ADMIN -> ORG_ADMIN`
2. 前端和接口不得再引入新的层级角色名，例如 `CITY_ADMIN`

## 3. 赛事级别边界

冻结结论：

1. 市级赛事由市级组织管理员创建和发布
2. 区县赛事由区县组织管理员创建和发布
3. 校级赛事由学校管理员创建和发布
4. 市级赛事报名链路为“学校提交 -> 区县审核 -> 市级终审”
5. 区县赛事报名链路为“学校提交 -> 区县审核”
6. 校级赛事默认不走区县和市级审核

补充说明：

1. 第一阶段不做上级为下级批量下发赛事模板
2. 第一阶段不做复杂承办单位协同流

## 4. 报名审核状态机

冻结结论：

1. 使用以下状态：
   `DRAFT`
   `SUBMITTED`
   `DISTRICT_APPROVED`
   `DISTRICT_REJECTED`
   `CITY_APPROVED`
   `CITY_REJECTED`
   `WITHDRAWN`
2. 学校只能提交草稿态报名
3. 报名被驳回后回到草稿态修改
4. 报名未进入审核前允许撤回
5. 区县审核通过后，学校默认不可再直接改报名内容

补充说明：

1. 第一阶段不引入 `UNDER_REVIEW`、`MATERIAL_PENDING` 等额外状态
2. 审核轨迹单独保存在审核记录表中

## 5. 成绩模型口径

冻结结论：

1. 第一阶段成绩主值统一保存到 `result_value`
2. 同时保留 `rank_no` 和 `score_points`
3. 项目类型在赛事项目配置中表达，不在第一阶段拆分复杂成绩结构
4. 第一阶段允许基础排名，不做复杂纪录引擎

补充说明：

1. 这是一种 MVP 口径，不是最终完全体
2. 后续如需专业化，再拆分计时、计距、积分等结构

## 6. 上级对学生账号的权限边界

冻结结论：

1. 学校管理员拥有学生账号完整操作权限
2. 区县管理员默认只看本区统计汇总
3. 市级管理员默认只看全市统计汇总
4. 上级默认不看学校级学生账号明细
5. 上级默认不重置学生密码、不导出学生账号清单

补充说明：

1. 如后续确实需要代管，必须新增显式授权开关和审计日志
2. 第一阶段不做这种特殊代管能力

## 7. 第一阶段终端范围

冻结结论：

1. 第一阶段优先实现 Web 管理端
2. 第一阶段不要求 Flutter 管理端同步实现赛事能力
3. 教师端仅预留接口和最小扩展空间，不作为第一阶段主交付
4. 学生端第一阶段不接赛事主流程

补充说明：

1. 第一阶段重点验证组织树、权限范围、赛事主流程模型
2. 多端协同放到后续阶段推进

## 8. 上线迁移方式

冻结结论：

1. 第一阶段采用增量结构上线
2. 先上线表结构、实体和后端兼容代码
3. 组织树和赛事入口先按测试账号灰度开放
4. 学生账号现有使用方式保持不变
5. 不采用一次性切换策略

补充说明：

1. 若第一阶段只完成组织树底座，也允许先不上线赛事入口
2. 正式放量前必须至少完成一轮测试学校验证

## 四、对开发的直接约束

第一阶段开发必须遵守：

1. 不推翻现有学生账号主体模型
2. 不新增 `CITY_ADMIN`、`DISTRICT_ADMIN`、`SCHOOL_ADMIN` 这类层级角色常量作为核心模型
3. 新增权限判断优先走组织范围服务
4. 赛事先做 MVP，不追求完全体
5. 多端范围不扩张到第一阶段之外

## 五、建议的第一阶段交付范围

基于本冻结稿，第一阶段建议直接开发以下内容：

1. `Organization` 实体和组织树底座
2. `School` 与组织树绑定能力
3. `Teacher.managedOrg` 管理范围能力
4. 组织范围服务骨架
5. 学生账号按组织范围统计能力
6. 赛事实体和接口骨架

## 六、冻结稿生效说明

本文档作为当前分支 `feature/competition-system` 的第一版默认冻结口径。

若后续出现业务变更，建议按以下顺序处理：

1. 先更新本文档
2. 再更新清单、SQL 草案、权限矩阵、接口清单
3. 最后再改代码