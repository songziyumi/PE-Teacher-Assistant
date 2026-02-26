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
- `main`：主分支（生产），当前版本 v1.1.0
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
- 支持 Excel/CSV 批量导入学生数据
- 停止应用时用 `Ctrl+C`，避免 8080 端口残留占用
- `SchoolClassRepository` 提供 `findByKeyword`（关键词搜索）和 `findByFilters`（多条件过滤）两个分页查询方法

## 版本历史
- `v1.1.0`：管理模块分页、搜索与班级分配功能
  - 班级管理：按类型/年级/名称过滤、分页（15条/页）、批量删除、关键词搜索
  - 教师管理：班级分配功能，展示已分配班级列表
  - 考勤统计：多条件搜索（年级/班级/姓名/学号/身份证号）及分页
  - 学生管理：优化搜索与分页逻辑
- `v1.0.0`：初始版本，管理页面弹窗改造及首页班级过滤
