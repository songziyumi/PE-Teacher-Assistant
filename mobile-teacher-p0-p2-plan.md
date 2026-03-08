# 教师手机端功能落地清单（P0 / P1 / P2）

> 更新时间：2026-03-08  
> 状态标识：`[x] 已落地（代码已完成）` / `[-] 进行中（有代码，待联调验收）` / `[ ] 未开始`

## 目标
- 先打通教师端“学生管理 + 选课审批 + 消息联动”的核心闭环。
- 再完善效率、可追溯和体验能力。

## 当前总体进度（真实代码口径）
- P0：约 90%（核心功能与关键回归已具备，主要剩真机联调与验收打勾）
- P1：约 20%（消息未读筛选已具备，其他仍待开发）
- P2：约 0%（尚未启动）

## 迭代节奏（两周一迭代）
- 迭代1（P0）：2026-03-09 ~ 2026-03-22
- 迭代2（P0）：2026-03-23 ~ 2026-04-05
- 迭代3（P1）：2026-04-06 ~ 2026-04-19
- 迭代4（P1）：2026-04-20 ~ 2026-05-03
- 迭代5（P2）：2026-05-04 ~ 2026-05-17

## P0（必须优先上线）
- [x] 选课审批中心（待审批/已同意/已拒绝）
  - Flutter：`mobile/lib/screens/teacher/course_request_center.dart`
  - API：`GET /api/teacher/course-requests?status=...`
- [x] 审批动作（同意/拒绝/备注）+ 审批日志
  - Flutter：`mobile/lib/screens/teacher/course_request_detail.dart`
  - API：`POST /api/teacher/course-requests/{id}/approve|reject`
  - 后端：`CourseRequestAudit` + `course_request_audits`（JPA 自动建表）
- [x] 站内消息与审批单打通（消息可跳审批详情）
  - Flutter：`mobile/lib/screens/teacher/teacher_message_center.dart`
  - API：`GET /api/teacher/messages`（含 `businessTargetType/businessTargetId`）
- [-] 教师端学生编辑（姓名、性别、学号、学籍状态、行政班、选修班）稳定可用
  - Flutter：`mobile/lib/screens/teacher/teacher_student_list.dart`
  - API：`PUT /api/teacher/students/{id}`
  - 待完成：真机高频编辑回归（连续保存、边界值输入、并发修改）
- [x] 学号唯一校验（前端即时校验 + 后端强校验）
  - 前端：编辑弹窗防抖校验 `check-student-no`
  - 后端：`StudentService.ensureStudentNoUnique(...)`
- [x] 教师主页待办卡片（待审批数、未读消息数）
  - Flutter：`mobile/lib/screens/teacher/teacher_home.dart`
  - API：`GET /api/teacher/course-requests/summary`、`GET /api/teacher/messages/unread-count`

### P0 验收标准（落地版）
- [ ] 教师可在手机端 3 步内完成一条审批（列表 -> 详情 -> 同意/拒绝）
- [ ] 重复学号保存失败并返回明确提示（前端提示 + 后端 400 错误）
- [ ] 每条审批可追溯：申请人、审批人、审批时间、结果、备注
- [ ] 消息点击后可直接进入对应审批详情，不出现“找不到数据”

## P0 收尾任务（本周必须完成）
- [x] 数据库唯一索引补齐：`students(school_id, student_no)`（核验/修复脚本已补；本机与目标库均验证 `OK`）
- [-] 完成 1 轮教师账号真机联调（审批链路 + 消息跳转 + 学生编辑）
- [x] 补 6 条接口回归用例（接口层已补：`TeacherApiControllerRegressionTest`）：
  - 审批重复提交应失败
  - 非归属教师审批应失败
  - 审批后消息已读状态正确
  - 审批日志按时间倒序返回
  - 学号重复更新失败
  - 空备注/超长备注处理正确

## P1（增强效率与管理能力）
- [ ] 批量审批（批量同意/拒绝）
- [ ] 学生页多条件筛选（姓名、学号、行政班、选修班、学籍状态）
- [ ] 批量学生操作（批量改学籍状态、批量分配选修班）
- [-] 站内消息增强（未读筛选、按类型筛选、已读状态）
  - 已有：未读筛选 + 已读标记
  - 待补：按类型筛选（GENERAL / COURSE_REQUEST）
- [ ] 关键操作二次确认与失败重试机制

### P1 验收标准
- [ ] 50 条审批可在 3 分钟内完成批量处理
- [ ] 任意筛选条件组合下查询响应稳定
- [ ] 批量任务失败时可定位到具体失败记录

## P2（体验与运营能力）
- [ ] 推送通知（新申请、审批结果）
- [ ] 数据导出（审批记录、学生名单）
- [ ] 操作时间线（教师最近操作日志）
- [ ] 弱网容错（离线队列/重试/提交状态）
- [ ] 个人主页增强（课表、教学统计）

### P2 验收标准
- [ ] 推送到达率、点击进入成功率可统计
- [ ] 导出文件字段完整且与页面一致
- [ ] 弱网环境下不丢操作，重连后可恢复

## 技术任务分解（按端拆分）
### 后端 API
- [x] 审批列表 / 详情 / 动作 / 统计
- [x] 消息接口提供业务跳转目标（审批单 ID、类型）
- [x] 学生更新接口统一字段校验（含学号唯一）
- [ ] 批量审批接口

### 数据与审计
- [x] 审批日志表 + 消息审批字段（处理人/时间/备注）
- [x] 学号唯一索引（学校维度，脚本已补且本机/目标库验证 `OK`）
- [x] 审批状态机约束（待审批 -> 同意/拒绝）

### Flutter 端
- [x] 审批列表页、审批详情页、消息跳转
- [x] 学生编辑弹窗组件化（基础信息/行政班/选修班）
- [x] 统一错误提示与加载态（主要流程）
- [ ] 批量操作与重试组件

## 风险与应对
- 风险：审批与消息链路不一致导致跳转失败
- 应对：消息接口强制返回业务目标字段，并在详情接口做归属校验

- 风险：批量审批性能下降
- 应对：分页 + 批处理 + 异步写日志

- 风险：学号并发写入冲突
- 应对：服务层校验 + 数据库唯一索引双保险

## 建议执行顺序（继续开发）
1. 先补 P0 收尾：数据库索引 + 联调回归 + 验收打勾
2. 再做 P1 第一批：批量审批 + 消息类型筛选
3. 最后推进 P1 第二批：学生多条件筛选 + 批量学生操作

##  调试命令
mvn -q "-Dmaven.repo.local=.m2repo" -DskipTests compile                                                
mvn "-Dmaven.repo.local=.m2repo" spring-boot:run 
flutter devices
##  构建调试版APK
flutter build apk --debug --dart-define=API_BASE_URL=http://192.168.2.101:8080
##  构建正式版APK
flutter build apk --release --dart-define=API_BASE_URL=http://192.168.2.101:8080
##  将apk推送到手机
C:\Users\28970\AppData\Local\Android\Sdk\platform-tools\adb.exe push C:\Users\28970\Desktop\code\PE_TEACHER_ASSISTANT_JAVA\mobile\build\app\outputs\apk\release\app-release.apk /sdcard/Download/ 

##  直接执行（在 mobile 目录）                                                                                                                      
  1. 生成 keystore + key.properties                                                                                                               
  scripts\generate_keystore.bat -StorePass "Abc@123456" -KeyPass "Abc@123456"                                                                     
  2. 打正式 APK                                                                                                                                   
  scripts\build_release_apk.bat -ApiBaseUrl "http://192.168.2.101:8080"                                                                           
  更小
  scripts\build_release_apk.bat -ApiBaseUrl "http://175.24.131.74:8080" -SplitPerAbi
  产物目录：mobile\build\app\outputs\flutter-apk\                                                                                                  
  注意：                                                                                                                                          
  - 这两个密码必须自己记住，丢了就无法继续用同一签名更新应用。                                                                                    
  - 正式上架后，不要随便改 keystore 或密码。   
