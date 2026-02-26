# 体育教师助手系统 (PE Teacher Assistant)

## 项目概述
面向体育教师的 Web 管理系统，用于管理学生、班级、考勤和成绩。

## 技术栈
- Java 17
- Spring Boot 3.2.3
- Spring Security
- Spring Data JPA
- MySQL
- Thymeleaf 模板引擎
- Lombok
- Apache POI（Excel 导入导出）
- Apache Commons CSV

## 项目结构
```
src/main/java/com/pe/assistant/
├── config/          # 配置类（Security、数据初始化）
├── controller/      # 控制器（Admin、Attendance、Auth、Dashboard、Student）
├── entity/          # 实体类（Teacher、Student、SchoolClass、Grade、Attendance）
├── repository/      # JPA Repository 接口
├── security/        # Spring Security 用户认证
├── service/         # 业务逻辑（Attendance、Class、Grade、Student、Teacher）
└── PeTeacherAssistantApplication.java

src/main/resources/
├── application.yml          # 应用配置
├── static/css/style.css     # 样式文件
└── templates/               # Thymeleaf 模板
    ├── admin/               # 管理员页面（班级、学生、教师、成绩、考勤、导入、统计）
    ├── auth/                # 登录页面
    ├── teacher/             # 教师页面（考勤、学生列表）
    ├── dashboard.html       # 首页仪表盘
    └── layout.html          # 公共布局
```

## Git 分支
- `main`：主分支（生产），当前版本 v1.2.1
- `feature/attendance`：当前开发分支

## 常用命令
```bash
# 启动项目
mvn spring-boot:run

# 打包
mvn clean package -DskipTests

# 运行测试
mvn test
```

## 注意事项
- 数据库配置在 `src/main/resources/application.yml`
- 使用 Spring Security，登录认证通过 `UserDetailsServiceImpl` 实现
- 管理员和教师角色权限分离
- 支持 Excel/CSV 批量导入学生、班级、教师数据，以及批量修改学生选修课
- 停止应用时用 `Ctrl+C`，避免 8080 端口残留；若端口仍被占用，用 `powershell -Command "Stop-Process -Name java -Force"` 清理
- `SchoolClassRepository` 提供 `findByKeyword`（关键词搜索）和 `findByFilters`（多条件过滤）两个分页查询方法
- `SchoolClassRepository` 提供 `findByTeacherAndType(Teacher, String)` 按教师和班级类型查询
- `StudentRepository.findWithFilters` 使用 `DISTINCT` + 显式 `countQuery` 避免 LEFT JOIN 导致分页计数翻倍
- `StudentRepository.findWithFilters` 包含 `electiveClass` 字符串参数；控制器在 `classId` 对应选修课类型时，自动将其转为 `electiveClass = 班级名称` 并清空 `classId`，以正确按选修班搜索学生
- 考勤状态颜色规范：出勤=绿色(#27ae60)、缺勤=红色(#e74c3c)、请假=蓝色(#2980b9)
- 导出缺勤/请假文件名格式：`考勤日期+缺勤、请假名单.csv`，使用 RFC 5987 编码中文文件名
- Thymeleaf 模板中避免使用 `th:replace` 引用同页面 fragment，会导致内容重复渲染

## 版本历史
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
