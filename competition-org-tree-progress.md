# 组织树与赛事系统改造进度

## 当前分支

- 开发分支：`feature/competition-system`
- 基座提交：`00d582f` `Add organization tree and competition MVP foundation`
- 当前目标：完成本轮验收问题收口，继续推进浏览器联调与业务验收

## 本地里程碑与备份

- [x] `milestone/competition-mvp-foundation` -> `00d582f`
- [x] `backup/feature-competition-system` -> `00d582f`
- [x] `milestone/competition-admin-pages` -> `7f9359a`
- [x] `milestone/competition-pages-and-tests` -> `b1aa4b5`
- [x] `milestone/competition-pre-browser-polish` -> `a5bfb7f`
- [x] `backup/feature-competition-system-pre-browser-polish` -> `e0463e0`

## 最近完成的提交

- [x] `00d582f` 组织树与赛事 MVP 基础能力
- [x] `7fa2938` 新增赛事改造进度跟踪文件
- [x] `7f9359a` 新增赛事管理页面
- [x] `b1aa4b5` 增强赛事页面与回归测试
- [x] `518086e` 补充页面控制器与异常分支回归测试
- [x] `b532fc8` 补充报名负向回归测试并修复进度文档
- [x] `a5bfb7f` 收口赛事页面空状态、错误提示与联调前文案
- [x] `542ec0f` 同步远端备份 tag 与阶段进度
- [x] `d96175e` 修复验收问题并补齐组织管理员配置入口

## 1. 组织树改造

- [x] 建立 `Organization` 组织树实体
- [x] 建立 `OrganizationType` 组织层级枚举
- [x] 支持市级、区县、学校三级组织结构
- [x] 学校与组织节点建立绑定关系
- [x] 教师补充 `managedOrgId` 管理范围字段
- [x] 完成组织树基础查询、维护、绑定能力
- [x] 完成按组织范围推导可管理学校集合的能力
- [ ] 组织树后台维护页与更完整的运维工具仍可继续补强

## 2. 角色与权限收敛

- [x] 保留 `SUPER_ADMIN`、`TEACHER`、`STUDENT` 基础角色
- [x] 形成 `ORG_ADMIN` 作为组织管理员的目标口径
- [x] 形成“角色控制功能、组织树控制数据范围”的改造口径
- [x] 新增业务默认优先按组织范围控制数据访问
- [x] 赛事模块已经接入组织范围能力
- [x] 已提供超级管理员维护组织管理员账号的页面入口
- [x] 组织管理员创建改为用户名全局唯一，避免误覆盖学校管理员 / 教师账号
- [ ] 旧模块中仍按学校硬编码的权限判断需要继续迁移
- [ ] `ADMIN` 到 `ORG_ADMIN` 的彻底收口与兼容清理仍待推进

## 3. 学生账号兼容改造

- [x] 学生账号继续以学校为归属主体
- [x] 学生账号升级抽象与组织树方案没有冲突
- [x] 学生账号体系对赛事改造起到促进作用
- [x] 登录主体与管理范围职责已拆分
- [ ] 学生账号统计按市 / 区县 / 学校三级聚合的能力仍待补充
- [ ] 学生账号相关旧接口是否已全部接入组织范围校验，还需逐步排查

## 4. 赛事系统 MVP 后端

- [x] 建立 `Competition`
- [x] 建立 `CompetitionEvent`
- [x] 建立 `CompetitionRegistration`
- [x] 建立 `CompetitionRegistrationItem`
- [x] 建立 `CompetitionApprovalRecord`
- [x] 建立 `CompetitionResult`
- [x] 建立赛事级别、赛事状态、报名状态等核心枚举
- [x] 完成赛事创建、详情、列表、状态流转能力
- [x] 完成比赛项目创建与列表能力
- [x] 完成学校报名草稿创建、提交、明细维护能力
- [x] 完成区县审核、市级审核能力
- [x] 完成成绩录入、批量保存、发布能力
- [x] 市级 / 区县管理员可按管理范围查看可见报名记录
- [x] 已兼容赛事日期输入 `yyyy-MM-dd` 与 `yyyy-MM-ddTHH:mm[:ss]`
- [x] 已修复赛事创建成功但未实际落库的异常链路
- [x] `/api/**` 异常已统一返回 JSON，避免页面误判为成功

## 5. 赛事系统 Web 管理端

- [x] 新增赛事列表页
- [x] 新增赛事详情页
- [x] 列表页支持关键字、级别、状态筛选
- [x] 支持创建赛事、创建项目、修改赛事状态
- [x] 支持学校创建报名、维护报名明细、提交报名
- [x] 支持区县审核、市级审核
- [x] 支持成绩录入、按项目筛选、批量保存、发布结果
- [x] 详情页已暴露 `managedOrgType`、`activeTab`、权限布尔量等联调所需模型
- [x] 页面空状态、错误提示、确认弹窗和联调文案已做一轮收口
- [x] 页面请求已补齐 CSRF 头与登录态兼容处理
- [x] 创建赛事成功后优先跳转详情，失败时回退到列表定位
- [x] 已新增超级管理员入口 `/super-admin/org-admins`
- [x] 组织管理员配置页支持显示组织节点父级路径
- [x] 学校赛事项目表单已改为中文选项，并支持年级限报人数
- [ ] 浏览器真实联调后的页面异常清单仍可继续补充

## 6. 回归测试进度

- [x] `CompetitionAdminApiControllerRegressionTest`
- [x] `CompetitionRegistrationAdminApiControllerRegressionTest`
- [x] `CompetitionResultAdminApiControllerRegressionTest`
- [x] `AdminCompetitionPageControllerRegressionTest`
- [x] 覆盖赛事列表 / 创建 / 详情 / 状态流转
- [x] 覆盖项目列表 / 创建项目
- [x] 覆盖报名创建 / 提交 / 删除明细 / 区县审核 / 市级审核
- [x] 覆盖成绩列表 / 过滤 / 保存 / 批量保存 / 发布
- [x] 覆盖页面列表筛选、详情页分页签与权限模型暴露
- [x] 覆盖项目创建失败、成绩批量保存失败等异常分支
- [x] 覆盖报名详情不可见、报名明细不可见等负向分支
- [x] 覆盖页面接口异常返回 HTML / 非 JSON 时的失败分支
- [ ] 仍建议再做一次赛事模块定向总回归
- [ ] Flutter `dart analyze --no-fatal-warnings` 尚未执行

## 7. 当前分支状态

- [x] 当前工作可继续在 `feature/competition-system` 上推进
- [x] 当前本地可通过 Maven 编译
- [x] 关键赛事回归测试已具备持续扩展基础
- [x] 已新增联调前页面收口 tag：`milestone/competition-pre-browser-polish`
- [x] 已完成远端备份 tag：`backup/feature-competition-system-pre-browser-polish`
- [x] 已打本地恢复点 tag：`milestone/competition-mvp-foundation`
- [x] 当前分支已推送到远端：`origin/feature/competition-system`
- [x] 当前远端最新提交：`d96175e`

## 8. 还需要你重点验证的事项

- [x] 已确认项目可启动，Web 管理端可打开
- [x] 已确认超级管理员、学校管理员、教师可正常登录和退出
- [ ] 市级管理员、区县管理员、学校管理员三类账号的数据可见范围是否完全符合业务预期
- [ ] 新创建的组织管理员账号登录、赛事可见范围与操作权限仍需继续实测
- [ ] 报名提交、区县审核、市级审核、成绩发布的完整流程是否符合真实业务口径
- [ ] 学校创建赛事、创建项目、进入详情页后的完整交互仍建议继续走查
- [ ] 赛事详情页三个分页签和操作按钮显示是否符合预期

## 9. 下一步建议顺序

- [ ] 继续按验收清单实测市级管理员、区县管理员、组织管理员三类账号
- [ ] 走通“创建赛事 -> 创建项目 -> 报名 -> 审核 -> 成绩发布”完整链路
- [ ] 跑一次赛事模块定向总回归并收口本轮测试补强
- [ ] 根据后续联调结果继续修正页面细节、空状态和错误提示

