# 附件一：组织树、赛事系统与学生账号兼容改造 SQL 草案

## 一、使用说明

本文档提供数据库建表与增量改造的 SQL 草案，目标是为以下能力提供底座：

1. 组织树管理
2. 市、区县、学校三级赛事管理
3. 赛事报名、审核、成绩、公告
4. 学生账号的组织范围统计

说明：

1. 本文档以 MySQL 8 为假设基础
2. 字段命名遵循当前项目风格，以蛇形命名为主
3. 若现有库版本低于 MySQL 8，递归查询部分需要改写
4. 草案以“尽量兼容当前仓库”为原则，不强制推翻现有 `schools`、`teachers`、`students` 结构

## 二、增量改造原则

建议按以下顺序执行：

1. 先建新表
2. 再给旧表加字段
3. 再回填组织数据
4. 再补索引和约束
5. 最后再切业务代码

不建议：

1. 先删旧字段
2. 一次性重命名旧角色值
3. 一次性把所有学校表逻辑并入组织树

## 三、新增组织树表

```sql
CREATE TABLE organizations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    parent_id BIGINT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    contact_phone VARCHAR(20) NULL,
    address VARCHAR(200) NULL,
    full_name VARCHAR(300) NULL,
    path_ids VARCHAR(500) NULL,
    path_codes VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_organizations_code UNIQUE (code),
    CONSTRAINT fk_organizations_parent FOREIGN KEY (parent_id) REFERENCES organizations(id)
);
```

建议补充检查约束或由服务层保证：

1. `type` 仅允许 `CITY`、`DISTRICT`、`SCHOOL`
2. `CITY` 的 `parent_id` 必须为空
3. `DISTRICT` 的父节点必须是 `CITY`
4. `SCHOOL` 的父节点必须是 `DISTRICT`

建议索引：

```sql
CREATE INDEX idx_organizations_parent_id ON organizations(parent_id);
CREATE INDEX idx_organizations_type ON organizations(type);
CREATE INDEX idx_organizations_type_code ON organizations(type, code);
CREATE INDEX idx_organizations_enabled ON organizations(enabled);
```

## 四、改造学校表

### 1. 给 `schools` 增加组织节点映射

```sql
ALTER TABLE schools
ADD COLUMN org_id BIGINT NULL AFTER code;
```

```sql
ALTER TABLE schools
ADD CONSTRAINT fk_schools_org
FOREIGN KEY (org_id) REFERENCES organizations(id);
```

```sql
CREATE UNIQUE INDEX uk_schools_org_id ON schools(org_id);
CREATE INDEX idx_schools_enabled ON schools(enabled);
```

说明：

1. 一个学校只对应一个学校组织节点
2. 兼容期内先允许 `org_id` 为空，待迁移完成后再改为非空

迁移完成后可执行：

```sql
ALTER TABLE schools
MODIFY COLUMN org_id BIGINT NOT NULL;
```

### 2. 可选冗余字段

如果后期统计量较大，可考虑给 `schools` 增加冗余范围字段，但第一阶段不建议先加：

```sql
-- 可选，不建议第一阶段就做
-- ALTER TABLE schools ADD COLUMN district_org_id BIGINT NULL;
-- ALTER TABLE schools ADD COLUMN city_org_id BIGINT NULL;
```

## 五、改造教师表

### 1. 扩展管理员管理范围

```sql
ALTER TABLE teachers
ADD COLUMN managed_org_id BIGINT NULL AFTER school_id;
```

```sql
ALTER TABLE teachers
ADD CONSTRAINT fk_teachers_managed_org
FOREIGN KEY (managed_org_id) REFERENCES organizations(id);
```

```sql
CREATE INDEX idx_teachers_managed_org_id ON teachers(managed_org_id);
CREATE INDEX idx_teachers_role ON teachers(role);
CREATE INDEX idx_teachers_school_id ON teachers(school_id);
```

说明：

1. `SUPER_ADMIN` 的 `managed_org_id` 允许为空
2. 学校管理员应绑定学校组织节点
3. 区县管理员应绑定区县组织节点
4. 市级管理员应绑定市级组织节点
5. 普通教师可为空，或按学校节点回填，取决于后续是否要做统一组织范围查询

### 2. 角色兼容建议

现阶段不强制改字段长度，但建议确认 `teachers.role` 能存下 `ORG_ADMIN`。

如果字段长度足够，则无需改表；否则执行：

```sql
ALTER TABLE teachers
MODIFY COLUMN role VARCHAR(30) NOT NULL;
```

## 六、学生表与学生账号表

### 1. 学生表原则

`students` 原则上不需要为组织树增加新字段，因为学生仍然通过 `school_id` 归属学校。

建议保留现有约束：

```sql
-- 已有唯一约束示意
-- UNIQUE (school_id, student_no)
```

### 2. 学生账号表原则

`student_accounts` 不建议做结构性改造。学生账号系统继续保持：

1. 账号主体是学生
2. 学生通过学校归属组织树叶子节点
3. 上级只做统计，不默认做具体账号操作

如果要增强统计，可以在查询层完成，不必先改表。

## 七、新增赛事主表

```sql
CREATE TABLE competition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    level VARCHAR(20) NOT NULL,
    host_org_id BIGINT NOT NULL,
    undertake_org_id BIGINT NULL,
    school_year VARCHAR(20) NULL,
    term VARCHAR(20) NULL,
    status VARCHAR(30) NOT NULL,
    registration_start_at DATETIME NULL,
    registration_end_at DATETIME NULL,
    competition_start_at DATETIME NULL,
    competition_end_at DATETIME NULL,
    description TEXT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_competition_code UNIQUE (code),
    CONSTRAINT fk_competition_host_org FOREIGN KEY (host_org_id) REFERENCES organizations(id),
    CONSTRAINT fk_competition_undertake_org FOREIGN KEY (undertake_org_id) REFERENCES organizations(id),
    CONSTRAINT fk_competition_created_by FOREIGN KEY (created_by) REFERENCES teachers(id)
);
```

建议索引：

```sql
CREATE INDEX idx_competition_host_org_status ON competition(host_org_id, status);
CREATE INDEX idx_competition_level_status ON competition(level, status);
CREATE INDEX idx_competition_time_range ON competition(registration_start_at, registration_end_at);
```

## 八、新增赛事项目表

```sql
CREATE TABLE competition_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    competition_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    event_code VARCHAR(50) NOT NULL,
    gender_limit VARCHAR(20) NULL,
    group_rule VARCHAR(100) NULL,
    team_or_individual VARCHAR(20) NOT NULL,
    max_entries_per_school INT NULL,
    max_entries_per_district INT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_competition_event_competition FOREIGN KEY (competition_id) REFERENCES competition(id)
);
```

```sql
CREATE INDEX idx_competition_event_competition_id ON competition_event(competition_id);
CREATE INDEX idx_competition_event_enabled ON competition_event(enabled);
CREATE UNIQUE INDEX uk_competition_event_code ON competition_event(competition_id, event_code);
```

## 九、新增赛事分组表

```sql
CREATE TABLE competition_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    competition_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    school_stage VARCHAR(30) NULL,
    gender VARCHAR(20) NULL,
    grade_min INT NULL,
    grade_max INT NULL,
    birth_date_start DATE NULL,
    birth_date_end DATE NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_competition_group_competition FOREIGN KEY (competition_id) REFERENCES competition(id)
);
```

```sql
CREATE INDEX idx_competition_group_competition_id ON competition_group(competition_id);
```

## 十、新增报名主表

```sql
CREATE TABLE competition_registration (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    competition_id BIGINT NOT NULL,
    applicant_org_id BIGINT NOT NULL,
    applicant_school_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    submitted_by BIGINT NULL,
    submitted_at DATETIME NULL,
    current_approval_level VARCHAR(20) NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_registration_competition FOREIGN KEY (competition_id) REFERENCES competition(id),
    CONSTRAINT fk_registration_org FOREIGN KEY (applicant_org_id) REFERENCES organizations(id),
    CONSTRAINT fk_registration_school FOREIGN KEY (applicant_school_id) REFERENCES schools(id),
    CONSTRAINT fk_registration_submitted_by FOREIGN KEY (submitted_by) REFERENCES teachers(id)
);
```

```sql
CREATE INDEX idx_registration_competition_status ON competition_registration(competition_id, status);
CREATE INDEX idx_registration_school_status ON competition_registration(applicant_school_id, status);
CREATE INDEX idx_registration_org_status ON competition_registration(applicant_org_id, status);
CREATE UNIQUE INDEX uk_registration_competition_school ON competition_registration(competition_id, applicant_school_id);
```

## 十一、新增报名明细表

```sql
CREATE TABLE competition_registration_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    competition_event_id BIGINT NOT NULL,
    competition_group_id BIGINT NULL,
    team_name VARCHAR(100) NULL,
    role_type VARCHAR(30) NULL,
    seed_result VARCHAR(100) NULL,
    qualification_note VARCHAR(300) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_registration_item_registration FOREIGN KEY (registration_id) REFERENCES competition_registration(id),
    CONSTRAINT fk_registration_item_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_registration_item_event FOREIGN KEY (competition_event_id) REFERENCES competition_event(id),
    CONSTRAINT fk_registration_item_group FOREIGN KEY (competition_group_id) REFERENCES competition_group(id)
);
```

```sql
CREATE INDEX idx_registration_item_registration_id ON competition_registration_item(registration_id);
CREATE INDEX idx_registration_item_student_id ON competition_registration_item(student_id);
CREATE INDEX idx_registration_item_event_id ON competition_registration_item(competition_event_id);
CREATE UNIQUE INDEX uk_registration_item_unique_entry
ON competition_registration_item(registration_id, student_id, competition_event_id);
```

## 十二、新增审核记录表

```sql
CREATE TABLE competition_approval_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    approval_level VARCHAR(20) NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_org_id BIGINT NOT NULL,
    decision VARCHAR(20) NOT NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_registration FOREIGN KEY (registration_id) REFERENCES competition_registration(id),
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id) REFERENCES teachers(id),
    CONSTRAINT fk_approval_org FOREIGN KEY (approver_org_id) REFERENCES organizations(id)
);
```

```sql
CREATE INDEX idx_approval_registration_id ON competition_approval_record(registration_id);
CREATE INDEX idx_approval_org_level ON competition_approval_record(approver_org_id, approval_level);
```

## 十三、新增成绩表

```sql
CREATE TABLE competition_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    competition_id BIGINT NOT NULL,
    competition_event_id BIGINT NOT NULL,
    competition_group_id BIGINT NULL,
    student_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    district_org_id BIGINT NULL,
    result_value VARCHAR(100) NOT NULL,
    rank_no INT NULL,
    score_points DECIMAL(10,2) NULL,
    record_status VARCHAR(30) NOT NULL DEFAULT 'ENTERED',
    entered_by BIGINT NOT NULL,
    entered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_by BIGINT NULL,
    verified_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_result_competition FOREIGN KEY (competition_id) REFERENCES competition(id),
    CONSTRAINT fk_result_event FOREIGN KEY (competition_event_id) REFERENCES competition_event(id),
    CONSTRAINT fk_result_group FOREIGN KEY (competition_group_id) REFERENCES competition_group(id),
    CONSTRAINT fk_result_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_result_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_result_district_org FOREIGN KEY (district_org_id) REFERENCES organizations(id),
    CONSTRAINT fk_result_entered_by FOREIGN KEY (entered_by) REFERENCES teachers(id),
    CONSTRAINT fk_result_verified_by FOREIGN KEY (verified_by) REFERENCES teachers(id)
);
```

```sql
CREATE INDEX idx_result_competition_event_rank ON competition_result(competition_id, competition_event_id, rank_no);
CREATE INDEX idx_result_student_id ON competition_result(student_id);
CREATE INDEX idx_result_school_id ON competition_result(school_id);
CREATE INDEX idx_result_record_status ON competition_result(record_status);
```

## 十四、新增赛事公告表

```sql
CREATE TABLE competition_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    competition_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    publish_scope VARCHAR(30) NOT NULL,
    published_by BIGINT NOT NULL,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notice_competition FOREIGN KEY (competition_id) REFERENCES competition(id),
    CONSTRAINT fk_notice_published_by FOREIGN KEY (published_by) REFERENCES teachers(id)
);
```

```sql
CREATE INDEX idx_notice_competition_id ON competition_notice(competition_id);
CREATE INDEX idx_notice_published_at ON competition_notice(published_at);
```

## 十五、新增名额规则表

```sql
CREATE TABLE competition_quota_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    competition_id BIGINT NOT NULL,
    competition_event_id BIGINT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_org_id BIGINT NOT NULL,
    max_count INT NOT NULL,
    rule_note VARCHAR(300) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_quota_competition FOREIGN KEY (competition_id) REFERENCES competition(id),
    CONSTRAINT fk_quota_event FOREIGN KEY (competition_event_id) REFERENCES competition_event(id),
    CONSTRAINT fk_quota_org FOREIGN KEY (scope_org_id) REFERENCES organizations(id)
);
```

```sql
CREATE INDEX idx_quota_competition_org ON competition_quota_rule(competition_id, scope_org_id);
```

## 十六、初始化组织树数据草案

### 1. 创建市级节点

```sql
INSERT INTO organizations (name, code, type, parent_id, enabled, sort_order)
VALUES ('深圳市教育局', 'SZ_CITY', 'CITY', NULL, TRUE, 0);
```

### 2. 创建区县节点

```sql
INSERT INTO organizations (name, code, type, parent_id, enabled, sort_order)
SELECT '南山区教育局', 'SZ_NS', 'DISTRICT', id, TRUE, 10
FROM organizations
WHERE code = 'SZ_CITY';
```

### 3. 按学校批量创建学校节点

说明：以下 SQL 仅为示意，真正落地时需要按学校所属区县映射生成。

```sql
INSERT INTO organizations (name, code, type, parent_id, enabled, sort_order)
SELECT s.name,
       CONCAT('SCH_', s.code),
       'SCHOOL',
       d.id,
       TRUE,
       0
FROM schools s
JOIN organizations d ON d.code = 'SZ_NS'
WHERE s.org_id IS NULL;
```

### 4. 回填学校 `org_id`

```sql
UPDATE schools s
JOIN organizations o ON o.code = CONCAT('SCH_', s.code) AND o.type = 'SCHOOL'
SET s.org_id = o.id
WHERE s.org_id IS NULL;
```

## 十七、管理员迁移 SQL 草案

### 1. 将现有学校管理员回填为学校组织管理员

```sql
UPDATE teachers t
JOIN schools s ON t.school_id = s.id
SET t.managed_org_id = s.org_id
WHERE t.role = 'ADMIN'
  AND t.school_id IS NOT NULL
  AND s.org_id IS NOT NULL;
```

### 2. 可选：将旧 `ADMIN` 统一迁移为 `ORG_ADMIN`

不建议在第一阶段立即执行。若确认业务代码已兼容，再执行：

```sql
UPDATE teachers
SET role = 'ORG_ADMIN'
WHERE role = 'ADMIN';
```

## 十八、学生账号统计视图草案

### 1. 按学校统计学生账号情况

```sql
CREATE OR REPLACE VIEW v_student_account_school_stats AS
SELECT
    st.school_id,
    COUNT(st.id) AS student_count,
    SUM(CASE WHEN sa.id IS NOT NULL THEN 1 ELSE 0 END) AS account_count,
    SUM(CASE WHEN sa.activated = TRUE AND sa.password_reset_required = FALSE THEN 1 ELSE 0 END) AS activated_count,
    SUM(CASE WHEN sa.enabled = FALSE THEN 1 ELSE 0 END) AS disabled_count,
    SUM(CASE WHEN sa.locked = TRUE THEN 1 ELSE 0 END) AS locked_count,
    SUM(CASE WHEN sa.id IS NULL THEN 1 ELSE 0 END) AS missing_count
FROM students st
LEFT JOIN student_accounts sa ON sa.student_id = st.id
GROUP BY st.school_id;
```

### 2. 按区县汇总学生账号情况

如果使用 MySQL 8，可以通过组织树关系聚合学校：

```sql
SELECT
    d.id AS district_org_id,
    d.name AS district_name,
    SUM(v.student_count) AS student_count,
    SUM(v.account_count) AS account_count,
    SUM(v.activated_count) AS activated_count,
    SUM(v.disabled_count) AS disabled_count,
    SUM(v.locked_count) AS locked_count,
    SUM(v.missing_count) AS missing_count
FROM organizations d
JOIN organizations so ON so.parent_id = d.id AND so.type = 'SCHOOL'
JOIN schools s ON s.org_id = so.id
JOIN v_student_account_school_stats v ON v.school_id = s.id
WHERE d.type = 'DISTRICT'
GROUP BY d.id, d.name;
```

## 十九、推荐上线脚本顺序

建议部署顺序：

1. 执行新表建表语句
2. 执行旧表加字段语句
3. 初始化组织数据
4. 回填学校和管理员组织关联
5. 创建索引
6. 创建统计视图
7. 上线兼容代码
8. 验证通过后，再考虑把 `schools.org_id` 改为非空

## 二十、回滚建议

如果新功能上线后需要回滚，建议：

1. 保留新表，不立即删除
2. 业务代码切回旧权限逻辑
3. 停用组织树入口页面
4. 不要急于清理 `managed_org_id` 和 `org_id`

原因是组织树和赛事表大多是增量结构，保留它们比强制回滚结构更安全。