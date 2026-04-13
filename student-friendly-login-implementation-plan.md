# 学生友好登录改造开发清单

## 1. 目标

本方案用于解决当前学生 `loginId` 难记、难输入、首登体验差的问题，同时避免一次性切换到“仅手机号登录”带来的高风险改动。

本次改造按 3 期推进：

1. 一期：缩短系统 `loginId`
2. 二期：首次登录绑定“便捷登录账号”
3. 三期：支持“双通道登录”并统一登录风控

原则：

- 保留系统 `loginId` 作为兜底账号，不直接废弃
- 新增“便捷登录账号”能力，优先解决易用性
- 学生登录改造不能影响教师/管理员登录
- 所有关键点保留可回退路径

---

## 2. 范围与边界

### 2.1 本次纳入范围

- 学生账号生成规则
- 学生首次登录改密流程
- 学生登录标识扩展
- 学生登录风险控制
- 管理端学生账号展示与导出
- Web 学生端与 Flutter 学生端登录/改密页面

### 2.2 本次不纳入范围

- 忘记密码短信找回
- 家长端独立账号体系
- 教师/管理员账号体系重构
- 彻底删除历史 `loginId`

---

## 3. 当前实现基线

当前项目的学生认证核心如下：

- 学生账号实体：`src/main/java/com/pe/assistant/entity/StudentAccount.java`
- 学生账号仓库：`src/main/java/com/pe/assistant/repository/StudentAccountRepository.java`
- 学生账号服务：`src/main/java/com/pe/assistant/service/StudentAccountService.java`
- 登录认证入口：`src/main/java/com/pe/assistant/security/UserDetailsServiceImpl.java`
- 学生登录 API：`src/main/java/com/pe/assistant/controller/api/AuthApiController.java`
- 学生改密 API：`src/main/java/com/pe/assistant/controller/api/StudentApiController.java`
- 学生 Web 改密页：`src/main/resources/templates/student/password.html`
- 学生 Flutter 改密页：`mobile/lib/screens/student/student_password_screen.dart`

当前问题：

- 系统 `loginId` 为随机串，输入成本高
- 登录入口只有一个“账号”字段，但没有更友好的登录标识
- 失败锁定按“输入字符串”计数，后续如果支持多个登录入口会有绕过风险
- 教师账号已经使用手机号作为用户名，学生新增手机号登录必须考虑冲突

---

## 4. 分期方案总览

### 4.1 一期目标

把新生成的学生系统账号改短，优先降低“首登输入难度”。

### 4.2 二期目标

学生首次登录后，在修改密码的同时绑定“便捷登录账号”。

便捷登录账号支持两类：

- 自定义短账号
- 手机号

### 4.3 三期目标

登录时同时支持：

- 教师用户名
- 学生便捷登录账号
- 学生系统 `loginId`

并把失败锁定从“按输入字符串”改成“按实际账号主体”计数。

---

## 5. 一期：缩短系统 `loginId`

## 5.1 目标

- 新发放学生账号更短、更易输入
- 不引入短信平台
- 不改变现有登录流程
- 不影响已激活老账号

## 5.2 建议规则

- 现状：`S + 8 位随机字符`
- 目标：`S + 6 位数字`

示例：

- `S381204`
- `S904517`

说明：

- 数字比混合字母数字更适合学生手工输入
- 保留前缀 `S`，便于人工识别“学生系统账号”
- 6 位数字在单校级别通常足够，配合唯一性检查可用

## 5.3 后端开发任务

- [ ] 修改 `src/main/java/com/pe/assistant/service/StudentAccountService.java`
- [ ] 调整 `LOGIN_ID_RANDOM_LENGTH`
- [ ] 调整 `LOGIN_ID_CHARS` 为纯数字字符集
- [ ] 保留 `generateUniqueLoginId()` 的唯一性重试逻辑
- [ ] 确认 `resetPassword()` 不强制改已有 `loginId`
- [ ] 确认 `regenerateAccount()` 在需要时可重新生成短 `loginId`
- [ ] 新增“批量重发短系统账号”策略说明，不直接改已激活账号

## 5.4 管理端开发任务

- [ ] 检查学生账号导出文案是否仍适用于短账号
- [ ] 检查学生账号管理列表是否需要突出显示“系统账号”
- [ ] 如需支持“重发短账号”，补充管理员操作入口
- [ ] 如需支持“只为未激活账号重发”，补充操作说明与限制

建议关注文件：

- `src/main/java/com/pe/assistant/controller/AdminStudentAccountController.java`
- `src/main/java/com/pe/assistant/controller/api/AdminStudentAccountApiController.java`
- `src/main/resources/templates/admin/student-accounts.html`
- `mobile/lib/screens/admin/student_account_screen.dart`

## 5.5 学生端开发任务

- [ ] 调整 Web 登录页文案，减少“系统账号很复杂”的隐性认知
- [ ] 调整 Flutter 登录页文案，提示“系统账号为学校发放账号”
- [ ] 保持登录表单字段名不变，避免一期改动过大

建议关注文件：

- `src/main/resources/templates/auth/login.html`
- `mobile/lib/screens/auth/login_screen.dart`

## 5.6 测试任务

- [ ] 为 `StudentAccountService` 增补短 `loginId` 生成测试
- [ ] 验证生成账号不会与历史账号冲突
- [ ] 验证重置密码不改变既有账号
- [ ] 验证重新生成账号时会产生短 `loginId`
- [ ] 验证 Web/Flutter 登录页文案更新不影响原流程

## 5.7 一期验收标准

- [ ] 新发学生系统账号长度明显缩短
- [ ] 历史账号仍可正常登录
- [ ] 管理员重置密码流程不受影响
- [ ] 学生首登、改密、进入系统流程保持正常

---

## 6. 二期：首次登录绑定便捷登录账号

## 6.1 目标

- 学生首次登录时，除修改密码外，再设置一个更容易记忆的登录账号
- 不替换系统 `loginId`
- 便捷登录账号优先用于日常登录
- 系统 `loginId` 保留兜底

## 6.2 数据模型设计

建议在 `student_accounts` 表新增字段：

- `login_alias`：便捷登录账号，允许为空，唯一
- `login_alias_type`：`CUSTOM` / `PHONE`
- `login_alias_verified`：是否已验证
- `login_alias_bound_at`：绑定时间

建议：

- `login_alias` 统一存“规范化值”
- 自定义短账号统一转小写
- 手机号只存纯数字

## 6.3 SQL 任务

- [ ] 新增数据库迁移脚本，文件建议命名为 `scripts/db/add_student_login_alias.sql`
- [ ] 为 `student_accounts.login_alias` 建唯一索引
- [ ] 评估是否需要给 `login_alias_type` 加枚举说明
- [ ] 编写校验脚本，文件建议命名为 `scripts/db/check_student_login_alias.sql`
- [ ] 明确回滚策略：只删除新增列与索引，不影响原 `login_id`

建议脚本内容至少包含：

- 表结构变更
- 索引创建
- 空值兼容
- 历史数据兼容校验

## 6.4 后端实体与仓库任务

- [ ] 更新 `src/main/java/com/pe/assistant/entity/StudentAccount.java`
- [ ] 新增 `loginAlias`
- [ ] 新增 `loginAliasType`
- [ ] 新增 `loginAliasVerified`
- [ ] 新增 `loginAliasBoundAt`
- [ ] 更新 `src/main/java/com/pe/assistant/repository/StudentAccountRepository.java`
- [ ] 新增 `findByLoginAliasIgnoreCase(...)`
- [ ] 新增 `existsByLoginAliasIgnoreCase(...)`

## 6.5 后端服务任务

核心服务集中在：

- `src/main/java/com/pe/assistant/service/StudentAccountService.java`

需要新增/调整的能力：

- [ ] 新增便捷账号规范化方法
- [ ] 新增便捷账号格式校验方法
- [ ] 新增手机号格式校验方法
- [ ] 新增跨角色唯一性校验方法
- [ ] 新增绑定便捷账号方法
- [ ] 新增“改密并绑定便捷账号”事务方法
- [ ] 调整 `changePassword(...)`，支持一期后平滑迁移到“改密 + 绑定”

建议新增的方法：

- `normalizeLoginAlias(...)`
- `validateLoginAlias(...)`
- `validatePhoneAlias(...)`
- `assertLoginPrincipalAvailable(...)`
- `bindLoginAlias(...)`
- `changePasswordAndBindAlias(...)`

## 6.6 跨角色唯一性任务

必须同时防止以下冲突：

- 学生 `loginId` 与学生 `loginAlias` 冲突
- 学生 `loginAlias` 与教师 `username` 冲突
- 新建教师时与学生已有 `loginAlias` 冲突

需要改造：

- [ ] 在学生绑定便捷账号时联查教师用户名
- [ ] 在教师创建/导入时联查学生便捷账号
- [ ] 明确冲突错误提示，避免用户看不懂

建议关注文件：

- `src/main/java/com/pe/assistant/service/TeacherService.java`
- `src/main/java/com/pe/assistant/repository/TeacherRepository.java`
- `src/main/java/com/pe/assistant/controller/AdminController.java`

## 6.7 Web 学生端任务

- [ ] 改造 `src/main/resources/templates/student/password.html`
- [ ] 在改密页增加“便捷登录账号”输入区域
- [ ] 增加“账号类型”选择：自定义短账号 / 手机号
- [ ] 增加格式提示与唯一性错误提示
- [ ] 强制首登时必须同时完成改密和绑定
- [ ] 非强制场景下允许后续进入“账号设置”页面再绑定/修改

页面建议字段：

- 当前系统账号（只读）
- 当前密码
- 新密码
- 确认新密码
- 便捷登录账号类型
- 便捷登录账号

## 6.8 Flutter 学生端任务

- [ ] 改造 `mobile/lib/screens/student/student_password_screen.dart`
- [ ] 与 Web 端保持相同字段和校验规则
- [ ] 登录成功后刷新当前用户信息
- [ ] 完成绑定后优先展示便捷登录账号

如接口拆分，还需同步调整：

- `mobile/lib/services/student_service.dart`
- `mobile/lib/services/auth_service.dart`
- `mobile/lib/providers/auth_provider.dart`
- `mobile/lib/models/user.dart`

## 6.9 管理端任务

- [ ] 学生账号列表新增“便捷账号”列
- [ ] 若便捷账号为手机号，列表中默认脱敏显示
- [ ] 导出列表时明确区分“系统账号”和“便捷账号”
- [ ] 为管理员增加“已绑定/未绑定便捷账号”筛选

建议关注文件：

- `src/main/resources/templates/admin/student-accounts.html`
- `mobile/lib/screens/admin/student_account_screen.dart`
- `src/main/java/com/pe/assistant/controller/AdminStudentAccountController.java`
- `src/main/java/com/pe/assistant/controller/api/AdminStudentAccountApiController.java`

## 6.10 API 任务

建议新增或调整以下接口：

- [ ] 扩展学生改密接口，支持同时提交便捷账号
- [ ] 如前后端联调需要，新增“检查账号是否可用”接口
- [ ] 返回当前用户信息时优先展示便捷账号

建议接口方向：

- `POST /api/student/password/change`
- `POST /api/student/account/check-alias`
- `GET /api/auth/me`

## 6.11 测试任务

- [ ] 新增学生便捷账号格式校验测试
- [ ] 新增学生手机号格式校验测试
- [ ] 新增学生绑定便捷账号唯一性测试
- [ ] 新增学生与教师账号冲突测试
- [ ] 新增“改密并绑定”事务测试
- [ ] 新增 Web 首登强制绑定流程测试
- [ ] 新增 Flutter 首登绑定流程联调检查

## 6.12 二期验收标准

- [ ] 学生首登必须完成改密与便捷账号绑定
- [ ] 便捷账号全局唯一
- [ ] 教师手机号/用户名不会与学生便捷账号撞号
- [ ] 已绑定学生能在个人信息中看到便捷账号
- [ ] 管理员能区分系统账号与便捷账号

---

## 7. 三期：双通道登录与统一风控

## 7.1 目标

学生登录时同时支持：

- 系统 `loginId`
- 便捷登录账号

教师/管理员仍保持原登录方式不变。

## 7.2 登录解析顺序

建议顺序：

1. 先查教师 `username`
2. 再查学生 `loginAlias`
3. 最后查学生 `loginId`

这样可以最大程度兼容当前教师体系，同时满足学生友好登录。

## 7.3 核心认证改造任务

重点文件：

- `src/main/java/com/pe/assistant/security/UserDetailsServiceImpl.java`
- `src/main/java/com/pe/assistant/service/StudentAccountService.java`

任务清单：

- [ ] 抽取统一的“学生登录标识解析”方法
- [ ] 登录时优先按 `loginAlias` 查学生
- [ ] 未命中便捷账号时回退到 `loginId`
- [ ] 保持认证成功后的主体仍为 `student-account:{id}`
- [ ] 保持 JWT 主体仍为学生账号主体，不直接使用别名

建议新增方法：

- `resolveStudentByLoginInput(...)`
- `resolveLoginPrincipal(...)`

## 7.4 登录返回与当前用户信息任务

重点文件：

- `src/main/java/com/pe/assistant/controller/api/AuthApiController.java`

任务清单：

- [ ] 登录成功后优先返回便捷账号
- [ ] `/api/auth/me` 优先返回便捷账号
- [ ] 如无便捷账号，则回退返回系统 `loginId`
- [ ] 明确前端展示字段含义：展示用账号不等于内部唯一主键

## 7.5 登录失败锁定改造任务

当前风险：

- 现在失败次数按“输入字符串”记录
- 若学生可同时输入 `loginId` 和 `loginAlias`，可通过切换输入值绕过限制

建议方案：

- 教师继续按教师用户名计数
- 学生改成按 `student-account:{id}` 计数
- 对无法解析的陌生输入，仍按原始输入记匿名失败次数

重点改造文件：

- `src/main/java/com/pe/assistant/security/LoginAttemptService.java`
- `src/main/java/com/pe/assistant/security/UserDetailsServiceImpl.java`
- `src/main/java/com/pe/assistant/config/SecurityConfig.java`
- `src/main/java/com/pe/assistant/controller/api/AuthApiController.java`

任务清单：

- [ ] 为登录失败计数引入“标准化尝试键”
- [ ] 学生登录命中账号后统一映射为 `student-account:{id}`
- [ ] 教师登录统一映射为教师用户名
- [ ] 登录成功时清理对应标准化尝试键
- [ ] 保留对未知输入的基础防爆破能力

建议新增方法：

- `resolveAttemptKey(...)`
- `loginFailedByResolvedPrincipal(...)`
- `loginSucceededByResolvedPrincipal(...)`

## 7.6 Web 与 Flutter 登录页任务

- [ ] Web 登录页提示改为“系统账号 / 便捷账号均可登录”
- [ ] Flutter 登录页提示同步更新
- [ ] 若便捷账号已绑定手机号，优先引导学生用手机号登录
- [ ] 但不在页面上暴露“系统账号已废弃”的错误认知

建议关注文件：

- `src/main/resources/templates/auth/login.html`
- `mobile/lib/screens/auth/login_screen.dart`

## 7.7 回归测试任务

- [ ] 教师用户名登录回归测试
- [ ] 学生系统账号登录回归测试
- [ ] 学生便捷账号登录回归测试
- [ ] 学生错误密码锁定测试
- [ ] 学生切换 `loginId` / `loginAlias` 无法绕过锁定测试
- [ ] 登录成功后锁定状态清理测试
- [ ] `/api/auth/me` 展示账号正确性测试

## 7.8 三期验收标准

- [ ] 学生可使用系统账号登录
- [ ] 学生可使用便捷账号登录
- [ ] 教师登录完全不受影响
- [ ] 登录失败限制不能被双账号入口绕过
- [ ] 前端展示账号与认证主体职责清晰

---

## 8. 可选四期：手机号验证码绑定

说明：

- 本期不是当前三步主线的必做项
- 建议在二期便捷账号能力稳定后，再追加

## 8.1 建议目标

- 手机号作为便捷账号时，必须经过验证码校验后才正式生效
- 自定义短账号可继续保留，不强制所有学生必须手机号登录

## 8.2 建议新增能力

- 发送绑定验证码
- 验证绑定验证码
- 发送频控
- 过期控制
- 日志审计

## 8.3 建议接口

- [ ] `POST /api/student/account/send-bind-code`
- [ ] `POST /api/student/account/verify-bind-code`

## 8.4 实现建议

- 优先用数据库表存验证码状态
- 暂不要求 Redis
- 短期不要把“验证码登录”与“验证码绑定”一起上

---

## 9. 发布顺序建议

建议按以下顺序发布：

### 9.1 第一次发布

- 仅上线一期：短系统账号

### 9.2 第二次发布

- 上线二期：首登绑定便捷账号
- 新账号开始强制绑定

### 9.3 第三次发布

- 上线三期：双通道登录与统一风控
- 登录页文案同步切换

### 9.4 第四次发布（可选）

- 上线手机号验证码绑定

---

## 10. 回滚策略

- 一期回滚：恢复原 `loginId` 生成规则，不影响历史数据
- 二期回滚：前端停止展示便捷账号绑定入口，数据库新增字段可暂时保留
- 三期回滚：登录解析回退到“教师用户名 + 学生 `loginId`”
- 四期回滚：关闭验证码发送与验证接口，不影响既有账号体系

---

## 11. 开发顺序建议

建议按以下开发顺序执行：

1. 完成一期后端生成规则调整
2. 完成一期文案与回归验证
3. 完成二期数据库迁移
4. 完成二期后端实体/仓库/服务改造
5. 完成二期 Web 首登绑定页面
6. 完成二期 Flutter 首登绑定页面
7. 完成二期管理端展示与导出
8. 完成三期认证与风控改造
9. 完成三期登录页提示改造
10. 完成全链路回归

---

## 12. 最终交付清单

本方案最终交付时，至少应包含以下产物：

- [ ] SQL 迁移脚本
- [ ] SQL 校验脚本
- [ ] 后端实体与仓库代码
- [ ] 后端服务与接口代码
- [ ] Web 学生端页面改造
- [ ] Flutter 学生端页面改造
- [ ] 管理端列表与导出改造
- [ ] 单元测试/回归测试
- [ ] 操作手册或上线说明

---

## 13. 推荐优先级

如资源有限，建议优先级如下：

### P0

- 一期全部任务
- 二期中的“自定义短账号绑定”

### P1

- 三期双通道登录
- 三期统一风控
- 二期中的管理端展示增强

### P2

- 手机号验证码绑定
- 更细的账号可用性检查接口
- 更多管理端筛选与统计

---

## 14. 决策建议

当前最稳妥的实施方案是：

- 先做短系统账号
- 再做首登绑定便捷账号
- 再做双通道登录
- 最后按需要补手机号验证码

不建议直接一步切到“只能手机号登录”，否则会同时放大：

- 开发复杂度
- 迁移风险
- 登录失败风控复杂度
- 教师/学生账号冲突风险

---

## 15. P0 / P1 / P2 开发排期

说明：

- 以下排期按“1 名后端 + 1 名前端/Flutter 兼顾开发”估算
- 如由 1 人独立完成，建议工期整体乘以 `1.5 ~ 2`
- 如中途插入短信验证码接入、批量历史数据修复或额外联调工作，工期需顺延

### 15.1 P0：先解决“难输入”，尽快可上线

目标：

- 先把系统账号变短
- 先让学生首登能绑定更好记的便捷账号
- 不做高风险认证重构

建议工期：

- `5 ~ 7` 个工作日

#### P0 范围

- 一期全部任务
- 二期中的“自定义短账号绑定”
- 必要的管理端展示调整
- 必要的测试与上线说明

#### P0 任务拆分

**第 1 天**

- [ ] 修改系统 `loginId` 生成规则
- [ ] 补充短账号生成测试
- [ ] 明确历史账号兼容策略

**第 2 天**

- [ ] 设计并落库 `login_alias` 相关字段
- [ ] 编写 SQL 迁移脚本与校验脚本
- [ ] 更新实体、仓库、基础服务

**第 3 天**

- [ ] 实现“改密并绑定自定义短账号”后端事务
- [ ] 实现唯一性校验
- [ ] 打通当前用户信息返回逻辑

**第 4 天**

- [ ] 改造 Web 首登改密页
- [ ] 改造 Flutter 首登改密页
- [ ] 改造登录页提示文案

**第 5 天**

- [ ] 改造管理端学生账号列表展示
- [ ] 增加“系统账号 / 便捷账号”展示
- [ ] 完成主要单测和人工回归

**缓冲 2 天**

- [ ] 修复联调问题
- [ ] 调整交互细节
- [ ] 补充上线说明与回滚说明

#### P0 交付物

- [ ] 新短系统账号生成能力
- [ ] 学生首登绑定自定义短账号能力
- [ ] 管理端可查看系统账号与便捷账号
- [ ] Web/Flutter 首登流程可用
- [ ] 基础回归测试通过

#### P0 上线收益

- 首登输入难度显著下降
- 学生日常登录开始摆脱随机串账号
- 不依赖短信平台，实施风险最低

---

### 15.2 P1：完成“双通道登录”与统一风控

目标：

- 让学生可以用“系统账号 + 便捷账号”都能登录
- 完成登录限流与锁定逻辑升级
- 让展示账号和认证主体职责清晰

建议工期：

- `4 ~ 6` 个工作日

前置依赖：

- P0 已上线并稳定
- 已有一批学生完成便捷账号绑定

#### P1 范围

- 三期全部任务
- 登录页提示更新
- 认证解析重构
- 登录限流按账号主体统一

#### P1 任务拆分

**第 1 天**

- [ ] 设计统一登录解析流程
- [ ] 实现学生 `loginAlias` / `loginId` 双通道解析
- [ ] 保持教师登录逻辑不变

**第 2 天**

- [ ] 改造登录成功返回逻辑
- [ ] 改造 `/api/auth/me` 返回逻辑
- [ ] 明确前端展示账号优先级

**第 3 天**

- [ ] 改造 `LoginAttemptService`
- [ ] 引入标准化尝试键
- [ ] 学生登录失败改按 `student-account:{id}` 计数

**第 4 天**

- [ ] 完成 Web/Flutter 登录页提示更新
- [ ] 增补登录回归测试
- [ ] 增补锁定绕过测试

**缓冲 2 天**

- [ ] 修复认证联调问题
- [ ] 修复锁定逻辑边界问题
- [ ] 完成生产发布前回归

#### P1 交付物

- [ ] 学生双通道登录能力
- [ ] 更稳的登录失败限制
- [ ] 教师账号零侵入兼容
- [ ] `/api/auth/me` 展示账号逻辑统一

#### P1 上线收益

- 已绑定学生可以彻底摆脱系统随机账号
- 双入口并存，便于平滑迁移
- 登录安全性不会因为双入口下降

---

### 15.3 P2：增强手机号能力与后续优化

目标：

- 在便捷账号稳定后，增加手机号绑定验证
- 完善账号体验、运营能力和管理能力

建议工期：

- `5 ~ 10` 个工作日

前置依赖：

- P1 已稳定
- 已确认短信平台选型、模板、预算、签名报备

#### P2 范围

- 手机号验证码绑定
- 管理端更多筛选与统计
- 更完善的账号设置能力
- 必要的审计与限流增强

#### P2 任务拆分

**阶段 A：手机号验证码绑定**

- [ ] 设计验证码存储表或状态表
- [ ] 接入短信发送服务
- [ ] 实现发送频控、过期控制、校验逻辑
- [ ] 将手机号绑定流程接入学生端

**阶段 B：管理与运营增强**

- [ ] 管理端增加“已绑定手机号/未绑定手机号”筛选
- [ ] 管理端增加便捷账号绑定统计
- [ ] 导出时支持显示便捷账号类型

**阶段 C：体验增强**

- [ ] 学生个人设置中支持修改便捷账号
- [ ] 对手机号进行脱敏展示
- [ ] 优化错误提示与帮助文案

#### P2 交付物

- [ ] 手机号验证码绑定能力
- [ ] 更完整的账号运营与统计能力
- [ ] 更完善的学生个人账号设置体验

#### P2 上线收益

- 手机号绑定可信度更高
- 后续才具备做“手机号优先登录”的基础
- 管理端可更清楚地跟踪账号改造进度

---

## 16. 里程碑建议

### M1：P0 完成

判定标准：

- 新账号已缩短
- 学生首登已可绑定自定义短账号
- 管理端能查看系统账号与便捷账号

### M2：P1 完成

判定标准：

- 学生可使用系统账号和便捷账号登录
- 登录失败限制不能通过双入口绕过
- 教师登录回归通过

### M3：P2 完成

判定标准：

- 手机号绑定验证码可用
- 管理端可查看手机号绑定情况
- 学生账号体系进入稳定运营阶段

---

## 17. 最终优先级建议

如果当前目标是“尽快改善学生登录体验”，建议执行顺序为：

1. **立即做 P0**
2. **P0 稳定后做 P1**
3. **是否做 P2 取决于短信平台和运营资源**

如果资源只能覆盖一轮开发，建议只做：

- 短系统账号
- 首登绑定自定义短账号

这是当前投入产出比最高、风险最低的组合。

---

## 18. 实施进度更新

更新时间：2026-04-11

当前状态：

- P0 已完成
- P1 已提前完成核心能力的一部分
- P2 未开始

### 18.1 P0 完成情况

已完成：

- [x] 新生成学生系统账号改为短账号规则：`S + 6 位数字`
- [x] 学生账号表新增便捷账号字段
- [x] 学生首次改密时支持绑定便捷账号
- [x] 便捷账号格式校验：4-20 位字母或数字
- [x] 便捷账号与学生系统账号唯一性校验
- [x] 便捷账号与教师用户名冲突校验
- [x] Web 登录页提示支持“系统账号 / 便捷账号”
- [x] Web 学生改密页新增便捷账号输入
- [x] Flutter 登录页提示支持“系统账号 / 便捷账号”
- [x] Flutter 学生改密页新增便捷账号输入
- [x] 管理端学生账号列表展示“系统账号 / 便捷账号”
- [x] 管理端导出增加“系统账号 / 便捷账号”
- [x] 新增数据库迁移脚本 `scripts/db/add_student_login_alias.sql`
- [x] 新增数据库校验脚本 `scripts/db/check_student_login_alias.sql`
- [x] 新增后端回归测试

验证情况：

- [x] Java 编译通过：`mvn -q "-Dmaven.repo.local=.m2repo" "-DskipTests" compile`
- [x] 新增 Java 回归测试通过：`mvn -q "-Dmaven.repo.local=.m2repo" "-Dtest=StudentAccountServiceRegressionTest,LoginPrincipalResolverTest,AuthApiControllerRegressionTest,StudentApiControllerRegressionTest" test`
- [x] Flutter 变更文件静态检查通过：`& 'D:\flutter\bin\cache\dart-sdk\bin\dart.exe' analyze --no-fatal-warnings lib/models/user.dart lib/services/student_service.dart lib/screens/student/student_password_screen.dart lib/screens/auth/login_screen.dart lib/models/student_account.dart lib/screens/admin/student_account_screen.dart`

### 18.2 P1 已提前完成部分

已完成：

- [x] 新增统一登录解析器
- [x] 学生登录支持系统账号 `loginId`
- [x] 学生登录支持便捷账号 `loginAlias`
- [x] 教师登录优先级保持不变
- [x] 学生认证成功后内部主体继续使用 `student-account:{id}`
- [x] API 登录返回新增 `loginAlias`
- [x] `/api/auth/me` 返回新增 `loginAlias`
- [x] 登录失败计数按解析后的账号主体收口

仍需补充：

- [x] P1 全链路人工回归
- [x] 登录失败锁定绕过场景基础自动化验证
- [x] 登录失败锁定绕过场景人工验证
- [x] 便捷账号唯一性冲突人工验证
- [x] 手机端 `loginId` / `loginAlias` 双通道登录人工验证
- [x] 教师登录、管理员登录自动化回归
- [x] 教师登录、管理员登录人工回归
- [ ] Flutter 全量静态分析完成
- [ ] 生产上线前 SQL 执行与校验确认

### 18.2.1 本轮 P1 回归验证与问题清理

已完成：

- [x] 补充 `AuthApiControllerRegressionTest`，覆盖学生登录返回 `loginAlias`
- [x] 补充 `/api/auth/me` 回归验证，确保学生端返回 `loginAlias`
- [x] 通过 `LoginPrincipalResolverTest` 验证学生 `loginAlias` / `loginId` 统一映射为 `student-account:{id}`
- [x] 补充教师、管理员 API 登录与 `/api/auth/me` 回归验证，确认 `loginAlias` 不影响非学生账号
- [x] 补充教师、管理员 `UserDetailsServiceImpl` 主体解析回归验证
- [x] 修复移动端强制改密跳转顺序，避免初始密码登录后短暂进入学生首页
- [x] 修复学生 JWT 主体 `student-account:{id}` 的后端解析，避免手机端后续接口报未授权
- [x] 修复移动端通知轮询仅教师启动，避免学生端误请求教师消息接口
- [x] 修复教师端通知启动异常可能影响登录体验的问题，通知失败不再阻断登录
- [x] 优化教师首页统计/未读消息加载容错，非关键接口失败不再阻断进入教师首页
- [x] 修复 `StudentApiControllerRegressionTest` 中选课事件时间构造，避免与当前控制器行为偏差
- [x] 清理 Flutter 管理端页面 3 处已废弃 `DropdownButtonFormField.value` 用法，改为 `initialValue`

结论：

- [x] 本轮 P1 目标范围内的自动化回归已通过
- [x] 手机端 `loginId` 与便捷账号登录人工验证已通过
- [x] 登录失败锁定绕过与便捷账号唯一性冲突人工验证已通过
- [x] 教师、管理员账号自动化回归已通过
- [x] 教师、管理员账号人工回归已通过
- [x] P1 全链路人工回归已完成
- [ ] 全仓 `mvn test` 仍存在历史遗留失败，需单独排期治理，不纳入本轮账号改造收口

### 18.3 P2 状态

尚未开始：

- [ ] 手机号验证码绑定
- [ ] 短信服务商接入
- [ ] 验证码发送频控
- [ ] 手机号脱敏展示
- [ ] 管理端手机号绑定统计

### 18.4 当前实际阶段判断

按原排期严格划分：

- 当前处于 **P0 完成，P1 人工回归完成**

按功能效果划分：

- 当前已经达到 **P1 核心能力与人工回归完成**

下一步建议：

1. 补完 Flutter 全量静态分析
2. 完成生产上线前 SQL 执行与校验确认
3. 再决定是否进入 P2 手机号验证码绑定
