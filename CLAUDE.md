# 体育教师助手系统 (PE Teacher Assistant)

## 项目概述
面向体育教师的 Web 管理系统，用于管理学生、班级、考勤和成绩。

## 技术栈
- Java 17
- Spring Boot 3.2.3
- Spring Security + JWT（jjwt 0.12.3，移动端无状态认证）
- Spring Data JPA
- MySQL
- Thymeleaf 模板引擎
- Lombok
- Apache POI（Excel 导入导出）
- Apache Commons CSV
- **Flutter 3.x**（移动端 App，`mobile/` 子目录）

## 项目结构
```
src/main/java/com/pe/assistant/
├── config/          # 配置类（Security、数据初始化）
├── controller/      # 控制器（Admin、Attendance、Auth、Dashboard、Student、SuperAdmin、
│                    #         PhysicalTest、TeacherPhysicalTest、TermGrade、TeacherTermGrade）
│   └── api/         # REST API 控制器（AuthApi、TeacherApi、AdminApi）
├── dto/             # 数据传输对象（ApiResponse、PageDto、LoginRequest、LoginResponse）
├── entity/          # 实体类（Teacher、Student、SchoolClass、Grade、Attendance、School、
│                    #         PhysicalTest、TermGrade）
├── repository/      # JPA Repository 接口
├── security/        # Spring Security 用户认证 + JwtUtil + JwtAuthFilter
├── service/         # 业务逻辑（Attendance、Class、Grade、Student、Teacher、School、CurrentUser、
│                    #           PhysicalTest、PhysicalScoring、TermGrade）
└── PeTeacherAssistantApplication.java

src/main/resources/
├── application.yml          # 应用配置
├── static/css/style.css     # 样式文件
└── templates/               # Thymeleaf 模板
    ├── admin/               # 管理员页面（班级、学生、教师、成绩、考勤、导入、统计、
    │                        #             体质健康测试、期末成绩管理）
    ├── auth/                # 登录页面
    ├── super-admin/         # 超级管理员页面（学校管理、管理员设置）
    ├── teacher/             # 教师页面（考勤、学生列表、体质测试录入、成绩录入）
    ├── fragments/           # 公共片段（footer 版本号）
    ├── dashboard.html       # 首页仪表盘
    └── layout.html          # 公共布局

mobile/                      # Flutter 移动端 App
├── pubspec.yaml
├── android/                 # Android 构建配置（gradle 使用腾讯云镜像）
├── ios/
└── lib/
    ├── main.dart            # 入口
    ├── app.dart             # GoRouter 路由（含角色守卫，initialLocation: '/login'）
    ├── config/api_config.dart   # 服务器地址配置（baseUrl）
    ├── models/              # 数据模型（User、SchoolClass、Grade、Student、
    │                        #           ElectiveClass、PhysicalTest、TermGrade）
    ├── services/            # API 调用（ApiService、AuthService、TeacherService、AdminService）
    ├── providers/           # 全局状态（AuthProvider）
    └── screens/
        ├── auth/            # 登录页
        ├── teacher/         # 教师端（首页、考勤、体测录入、成绩录入、学生列表）
        └── admin/           # 管理端（首页、学生列表、体测列表、成绩列表）
```

## Git 分支
- `main`：主分支（生产），当前版本 v2.1
- `flutter-mobile-app`：Flutter 移动端 App 分支（与 main 保持同步）
- `explore-1.5.2`：历史开发分支

## 常用命令
```bash
# 启动项目
mvn spring-boot:run

# 打包
mvn clean package -DskipTests

# 运行测试
mvn test

# Flutter 移动端
cd mobile
flutter run                        # 调试运行（需连接设备）
flutter build apk --release        # 构建 Android 正式 APK
# APK 输出路径：mobile/build/app/outputs/flutter-apk/app-release.apk
```

## 注意事项
- 数据库配置在 `src/main/resources/application.yml`
- 使用 Spring Security，登录认证通过 `UserDetailsServiceImpl` 实现
- 角色体系：SUPER_ADMIN（管理学校）→ ADMIN（管理本校数据）→ TEACHER（管理自己班级）
- 支持 Excel/CSV 批量导入学生、班级、教师数据，以及批量修改学生选修课
- 停止应用时用 `Ctrl+C`，避免 8080 端口残留；若端口仍被占用，用 `powershell -Command "Stop-Process -Name java -Force"` 清理
- `SchoolClassRepository` 提供 `findByKeyword`（关键词搜索）和 `findByFilters`（多条件过滤）两个分页查询方法
- `SchoolClassRepository` 提供 `findByTeacherAndType(Teacher, String)` 按教师和班级类型查询
- `StudentRepository.findWithFilters` 使用 `DISTINCT` + 显式 `countQuery` 避免 LEFT JOIN 导致分页计数翻倍
- `StudentRepository.findWithFilters` 包含 `electiveClass` 字符串参数；控制器在 `classId` 对应选修课类型时，自动将其转为 `electiveClass = 班级名称` 并清空 `classId`，以正确按选修班搜索学生
- 考勤状态颜色规范：出勤=绿色(#27ae60)、缺勤=红色(#e74c3c)、请假=蓝色(#2980b9)
- 导出缺勤/请假文件名格式：`考勤日期+缺勤、请假名单.csv`，使用 RFC 5987 编码中文文件名
- Thymeleaf 模板中避免使用 `th:replace` 引用同页面 fragment，会导致内容重复渲染
- `AdminController` 使用 `@ModelAttribute("currentSchool")` 自动向所有 admin 页面注入当前学校，模板用 `${currentSchool.name}` 显示
- `AttendanceController`、`StudentController` 同样用 `@ModelAttribute("currentSchool")` 向教师页面注入学校；`dashboard.html` 直接用 `${teacher.school.name}`
- `SchoolService.createOrResetAdmin`：用户名已存在时，仅允许重置**本学校**管理员密码，属于其他学校的账号一律拒绝并提示更换用户名
- `SchoolRepository` 提供 `existsByNameAndIdNot` / `existsByCodeAndIdNot`，用于编辑学校时排除自身的唯一性校验
- 选修课班级名称规范格式为 `"年级/班级名"`（如 `"高二/篮球"`），无年级时仅存班级名；`Student.electiveClass` 字段直接存储此格式字符串
- `StudentController` 与 `AttendanceController` 均通过 `electiveName(sc)` 辅助方法（返回 `String`）做严格精确匹配，不使用 IN 范围查询，防止同名选修班跨年级数据污染
- 批量导入班级时，若年级不存在会自动创建；Excel 中 FORMULA 类型单元格通过 `getCachedFormulaResultType()` 取实际值
- 导入教师分配班级时，`AdminController.matchesClass()` 支持 4 种格式匹配：`"篮球"`、`"高二篮球"`、`"高二/篮球"`、`"高二 篮球"`
- 批量导入反馈消息区分 `count`（新增）与 `skipDup`（已存在跳过），并列出前 20 条重复班级名称便于核查
- 清空考勤：`AttendanceRepository` 提供 `@Modifying deleteAllBySchool(School)`，管理员可在导入页一键清空本校全部考勤记录
- **体质健康测试**：`PhysicalTest` 实体唯一约束 `(student_id, academic_year, semester)`；`PhysicalScoringService` 按教育部2014标准评分（高中段，BMI 15% + 肺活量 15% + 50米跑 20% + 坐位体前屈 10% + 立定跳远 10% + 引体向上/仰卧起坐 10% + 1000m/800m 20%）；管理员路由 `/admin/physical-tests/**`，教师路由 `/teacher/physical-tests/**`（按班级批量录入）；录入页 JS 按性别禁用不适用字段（男→仰卧起坐/800m 禁用，女→引体向上/1000m 禁用），使用 `readOnly` 而非 `disabled` 以保持并行数组对齐
- **期末成绩管理**：`TermGrade` 实体唯一约束 `(student_id, academic_year, semester)`；综合分 = 出勤40% + 技能40% + 理论20%（空项按权重比例重分配）；管理员路由 `/admin/term-grades/**`，教师路由 `/teacher/term-grades/**`（按班级批量录入）
- **redirect URL 规范**：教师录入页 save-batch 使用 `URLEncoder.encode(semester, UTF_8)` 编码中文参数后再拼入 redirect，避免 HTTP Location 头含裸 UTF-8 导致浏览器停在 POST URL；所有 saveBatch 控制器方法均包 try-catch，确保无论成功失败都执行重定向
- **教师录入页学年默认值**：必须在学生查询 `if (classId != null)` 块**之前**计算，否则首次进入时 `academicYear` 为空导致学生列表不加载
- **Flutter REST API（双 FilterChain）**：`SecurityConfig` 中 `@Order(1)` FilterChain 处理 `/api/**`（无状态 JWT），`@Order(2)` 保留原 Session FilterChain；两者并存互不影响
- **JWT 认证**：`JwtUtil` 生成/验证 token，`JwtAuthFilter`（OncePerRequestFilter）仅处理 `/api/**`；token 存储于客户端 `flutter_secure_storage`；`/api/auth/**` 无需认证，`/api/teacher/**` 需 TEACHER/ADMIN，`/api/admin/**` 需 ADMIN
- **REST API 响应格式**：统一 `ApiResponse<T>{ code, message, data }`；分页用 `PageDto<T>{ content, totalElements, totalPages, page, size }`
- **移动端服务器地址**：`mobile/lib/config/api_config.dart` 中 `baseUrl` 配置；手机测试用局域网 IP，生产用公网 IP（当前已配置公网 175.24.131.74:8080）
- **Flutter 中国镜像**：系统环境变量 `PUB_HOSTED_URL=https://pub.flutter-io.cn`，`FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn`；Gradle 使用腾讯云镜像（`gradle-wrapper.properties`）
- **Flutter 路由守卫**：`app.dart` 中 GoRouter 设置 `initialLocation: '/login'`，redirect 中处理 `loc == '/'` 情况，防止登录后路由到未定义的 `/`
- **ElectiveClass 模型**：`storedName` 字段存储 `"年级/班级名"` 格式（与 `Student.electiveClass` 一致）；`displayName` getter 返回 `"年级 / 班级名"` 用于 UI 展示
- **年级联动下拉**：Web 端（`teacher/students.html`）和移动端（Flutter）的选修班修改弹窗均实现年级→选修班联动；修改行政班同样按年级过滤；均为纯前端实现，后端返回全量数据带 `gradeId` 字段
- **iOS 构建限制**：iOS App 必须在 Mac 上用 Xcode 构建，Windows 无法直接编译；iPhone 用户可用 Safari 访问 Web 端并「添加到主屏幕」作为替代

## 版本历史
- `v2.1`：Flutter 移动端 App + 学生管理增强
  - 新增 **Flutter 原生 App**（`mobile/` 目录，Android + iOS），教师端：班级列表、学生列表、考勤录入、体测录入、成绩录入；管理端：仪表盘、学生增删改查、体测/成绩列表
  - 新增 **REST API**（`/api/**`）：JWT 无状态认证，与原 Session/Thymeleaf 路由并存；`AuthApiController`、`TeacherApiController`、`AdminApiController`
  - 管理端学生 CRUD：新增 `POST /api/admin/students/save`、`DELETE /api/admin/students/{id}`
  - 教师端学生班级修改：新增 `PUT /api/teacher/students/{id}`，支持同时修改行政班和选修班
  - 新增 `GET /api/teacher/grades`、`/school-classes`、`/elective-classes` 支持年级联动
  - **Web 端**教师学生列表（`teacher/students.html`）修改选修班弹窗新增年级筛选联动
  - 修复 Flutter GoRouter 路由到未定义 `/` 导致的 GoException
- `v2.0`：教学管理功能层
  - 新增**体质健康测试**模块：管理员列表/导入/导出，教师按班级批量录入；内置教育部2014标准评分（高中段全项目）；录入页按性别自动禁用不适用字段
  - 新增**期末成绩管理**模块：管理员列表/编辑/导入/导出，教师按班级批量录入；综合分自动计算（出勤40% + 技能40% + 理论20%）；等级自动判定（优秀/良好/及格/不及格）
  - 教师首页班级卡片新增「体质测试」「成绩录入」快捷入口
  - 修复 redirect URL 含中文学期名称导致浏览器停留在 POST URL 的问题
- `v1.5.1`（原 explore-1.5.2 分支）：学生管理增强与UI优化
  - 选修班严格按 `"年级/班级名"` 精确匹配，`StudentController`/`AttendanceController` 从 IN 查询改为单值精确查询，修复跨年级同名选修班数据污染
  - 修复教师修改学生选修班后列表不刷新的问题（根因同上）
  - 批量导入班级：自动创建缺失年级；修复 FORMULA 单元格读取为空；选修课重复判断加入年级维度
  - 批量导入教师班级分配：`matchesClass()` 支持多种年级+班级名格式，修复跨年级同名选修班分配错误
  - 新增"清空全部考勤记录"功能（导入页，按学校隔离）
  - 成绩管理页 `th:onsubmit` 改为 `data-*` + JS，修复 500 错误
- `v1.4.0`：多学校管理平台（多租户架构）
  - 新增 School 实体，所有数据（Teacher/Student/SchoolClass/Grade）关联 school_id 实现学校级隔离
  - 新增 SUPER_ADMIN 角色：登录后跳转学校管理页，无法访问具体学校数据
  - 超级管理员模块：新增/编辑/删除学校，启用/禁用学校，为学校创建/重置管理员账号
  - 学校编辑：`SchoolRepository` 新增 `existsByNameAndIdNot`/`existsByCodeAndIdNot`，编辑时排除自身校验唯一性
  - 管理员账号安全：`createOrResetAdmin` 校验用户名归属，已被其他学校占用的用户名拒绝创建，防止账号被劫持
  - 学校名称展示：`AdminController` 用 `@ModelAttribute("currentSchool")` 自动注入，所有 admin 页面导航栏显示学校名；`AttendanceController`/`StudentController` 同样注入；`dashboard.html` 通过 `teacher.school.name` 显示
  - 超级管理员页面 footer 统一为版本号 + 版权（`fragments/footer :: version`）
  - CurrentUserService：从 SecurityContext 获取当前用户及所属学校，全局注入各控制器
  - DataInitializer：初始化 superadmin 账号和默认学校，自动将历史 school=null 数据迁移到默认学校
  - SecurityConfig：新增 `/super-admin/**` 路由权限，登录成功后按角色跳转不同首页
  - application.yml：新增 `app.super-admin.default-password` 可配置超管初始密码
- `v1.3.0`：安全加固
  - 登录防爆破：连续失败 5 次锁定 15 分钟（LoginAttemptService）
  - Session 安全：防 session fixation、最多 1 个并发会话、退出清除 JSESSIONID cookie
  - 错误信息脱敏：登录失败不再区分"用户名不存在"与"密码错误"
  - 全局异常处理：GlobalExceptionHandler 统一处理 403/500，新增对应错误页面
  - 文件上传校验：验证 xlsx 魔数（PK 文件头），防止伪造文件上传
  - 密码复杂度校验：重置密码要求 8 位以上且同时含字母和数字
  - 初始化安全：移除默认 teacher 账号；admin 初始密码改为可配置（`app.admin.default-password`）
  - JPQL 修复：LIKE 查询改用 `CONCAT('%', :param, '%')` 语法，修复部分数据库参数绑定问题
- `v1.2.1`：修复学生管理按选修班搜索无结果
  - `StudentRepository.findWithFilters` 新增 `electiveClass` 过滤条件
  - `AdminController` `/students` 和 `/stats` 接口：选中选修课类型班级时自动转换为 `electiveClass` 字符串查询
- `v1.2.0`：考勤与导入功能增强
  - 教师管理：删除教师前自动清除班级关联，修复外键约束 500 错误
  - 教师管理：独立搜索字段（姓名/用户名/手机号）、分页、统计
  - 仪表盘：行政班与选修班分开展示，修复选修班显示在行政班的问题
  - 考勤登记：合并行政班/选修班为单一表单，修复学生列表重复（108→54）
  - 学生管理：修复编辑弹窗 403（补充 CSRF token）
  - 考勤统计：出勤/缺勤/请假颜色区分
  - 缺勤查询：增加请假查询，导出包含请假记录，文件名含日期
  - 批量导入：新增"批量修改学生选修课"（按学号匹配，覆盖选修课字段）
- `v1.1.0`：管理模块分页、搜索与班级分配功能
  - 班级管理：按类型/年级/名称过滤、分页（15条/页）、批量删除、关键词搜索
  - 教师管理：班级分配功能，展示已分配班级列表
  - 考勤统计：多条件搜索（年级/班级/姓名/学号/身份证号）及分页
  - 学生管理：优化搜索与分页逻辑
- `v1.0.0`：初始版本，管理页面弹窗改造及首页班级过滤
