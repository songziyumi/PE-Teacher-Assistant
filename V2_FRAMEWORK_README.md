# 体育教师助手 v2.0 教学管理平台框架

## 项目概述
基于原有v1.x多租户管理平台，扩展v2.0教学管理功能，专注于体育教学的核心业务需求。

## 核心功能模块

### 1. 体质健康测试管理
- **实体类**: `HealthTestRecord`
- **Repository**: `HealthTestRecordRepository`
- **Service**: `HealthTestService`
- **Controller**: `HealthTestController`
- **功能特点**:
  - 教育部标准测试项目（身高、体重、BMI、肺活量等）
  - 自动BMI计算和等级评定
  - Excel批量导入导出
  - 学生成长曲线分析

### 2. 期末成绩管理系统
- **实体类**: `ExamRecord`
- **Repository**: `ExamRecordRepository`
- **Service**: `ExamService`
- **Controller**: `ExamController`
- **功能特点**:
  - 自定义考试项目和评分权重
  - 自动排名和统计分析
  - 不及格预警系统
  - 成绩分布可视化

### 3. 备课资源库
- **实体类**: `TeachingResource`
- **Repository**: `TeachingResourceRepository`
- **Service**: `ResourceService`
- **Controller**: `ResourceController`
- **功能特点**:
  - 教案、课件、视频资源管理
  - 分类存储和快速检索
  - 公开/私有权限控制
  - 资源下载统计

## 技术架构

### 后端架构
```
com.pe.assistant
├── controller/v2/          # v2.0控制器
│   ├── HealthTestController.java
│   ├── ExamController.java
│   ├── ResourceController.java
│   └── V2DashboardController.java
├── entity/                 # 实体类
│   ├── HealthTestRecord.java
│   ├── ExamRecord.java
│   └── TeachingResource.java
├── repository/             # 数据访问层
│   ├── HealthTestRecordRepository.java
│   ├── ExamRecordRepository.java
│   └── TeachingResourceRepository.java
├── service/               # 业务逻辑层
│   ├── HealthTestService.java
│   ├── ExamService.java
│   └── ResourceService.java
└── dto/                   # 数据传输对象（待扩展）
```

### 前端架构
```
src/main/resources/templates/v2/
├── layout.html            # 统一布局模板
├── overview.html          # 平台概览页面
├── health-test/           # 体质健康测试模块
│   ├── dashboard.html
│   └── add-form.html
├── exam/                  # 考试成绩模块
│   └── dashboard.html
└── resources/             # 资源库模块
    └── library.html
```

## 数据库设计

### 新增表结构
```sql
-- 体质健康测试记录表
CREATE TABLE health_test_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    test_date DATE NOT NULL,
    height DECIMAL(5,2),
    weight DECIMAL(5,2),
    bmi DECIMAL(4,2),
    lung_capacity INT,
    run_50m DECIMAL(4,2),
    sit_and_reach DECIMAL(4,2),
    standing_long_jump DECIMAL(4,2),
    pull_ups INT,
    sit_ups INT,
    run_1000m INT,
    run_800m INT,
    total_score INT,
    grade_level VARCHAR(20),
    created_at DATE,
    updated_at DATE
);

-- 考试成绩表
CREATE TABLE exam_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    exam_name VARCHAR(100) NOT NULL,
    exam_date DATE NOT NULL,
    project_1_name VARCHAR(50),
    project_1_score DECIMAL(5,2),
    project_2_name VARCHAR(50),
    project_2_score DECIMAL(5,2),
    project_3_name VARCHAR(50),
    project_3_score DECIMAL(5,2),
    project_4_name VARCHAR(50),
    project_4_score DECIMAL(5,2),
    project_5_name VARCHAR(50),
    project_5_score DECIMAL(5,2),
    total_score DECIMAL(5,2),
    class_rank INT,
    grade_rank INT,
    is_passed BOOLEAN,
    teacher_comment TEXT,
    created_at DATE,
    updated_at DATE
);

-- 教学资源表
CREATE TABLE teaching_resources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    file_url VARCHAR(500),
    file_type VARCHAR(50),
    file_size BIGINT,
    category VARCHAR(50),
    subject VARCHAR(50),
    grade_level VARCHAR(20),
    is_public BOOLEAN DEFAULT false,
    uploader_id BIGINT,
    download_count INT DEFAULT 0,
    view_count INT DEFAULT 0,
    created_at DATE,
    updated_at DATE
);
```

## 技术特性

### 1. 前端技术栈
- **Bootstrap 5**: 响应式UI框架
- **Thymeleaf**: 模板引擎
- **ECharts**: 数据可视化图表
- **Bootstrap Icons**: 图标库

### 2. 后端技术栈
- **Spring Boot 3.2.3**: 应用框架
- **Spring Data JPA**: 数据访问
- **Spring Security**: 安全认证
- **MySQL**: 数据库
- **Lombok**: 代码简化
- **Apache POI**: Excel处理

### 3. 设计模式
- **MVC架构**: 清晰的层次分离
- **Repository模式**: 数据访问抽象
- **Service模式**: 业务逻辑封装
- **DTO模式**: 数据传输对象

## 访问路径

### v2.0平台入口
- `/v2/overview` - v2.0平台概览
- `/v2/dashboard` - v2.0工作台

### 核心功能模块
- `/v2/health-test` - 体质健康测试管理
- `/v2/exam` - 期末成绩管理
- `/v2/resources` - 备课资源库

### 原有v1.x平台
- `/dashboard` - 原有工作台（已添加v2.0入口）

## 开发进度

### ✅ 已完成
1. 实体类设计（HealthTestRecord, ExamRecord, TeachingResource）
2. Repository接口定义
3. Service业务逻辑层
4. Controller控制器层
5. 基础模板页面
6. v2.0统一布局
7. 平台概览页面

### 🔄 进行中
1. 数据库表创建
2. 详细业务逻辑实现
3. Excel导入导出功能
4. 图表数据可视化

### 📋 待开发
1. 统计分析模块
2. 文件上传功能（集成腾讯云COS）
3. 移动端响应式优化
4. 权限控制细化

## 快速开始

### 1. 启动应用
```bash
mvn spring-boot:run
```

### 2. 访问平台
1. 访问 `http://localhost:8080/login`
2. 使用教师账号登录
3. 在dashboard页面点击"查看v2.0平台"
4. 开始使用v2.0教学管理功能

### 3. 创建数据库表
运行项目后，Spring Data JPA会自动创建表结构，或手动执行SQL脚本。

## 扩展建议

### 短期扩展
1. **Excel导入导出**: 实现Apache POI的详细功能
2. **图表分析**: 完善ECharts图表展示
3. **文件存储**: 集成腾讯云COS

### 中期规划
1. **学生账号系统**: 支持学生登录查看成绩
2. **选修课抢课**: 并发选课系统
3. **作业系统**: 课外作业提交和批改

### 长期愿景
1. **微信小程序**: 移动端应用
2. **运动会管理**: 赛事编排和成绩管理
3. **AI分析**: 基于数据的教学建议

## 注意事项

1. **数据库迁移**: 新表需要与原有表建立外键关系
2. **权限控制**: v2.0功能需要适当的权限控制
3. **数据安全**: 敏感数据需要加密存储
4. **性能优化**: 大数据量时的查询性能优化

## 联系支持

如有问题或建议，请联系项目负责人。

---
*最后更新: 2026-03-01*
*版本: v2.0-alpha*