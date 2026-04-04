# 抢课部署配置说明

> 适用范围：抢课高峰期部署、扩容与回滚  
> 相关配置文件：`src/main/resources/app-public.yml:1`

## 1. 档位说明

当前仓库已内置两套服务器档位：

- 低配：`server-low`
- 高配：`server-high`

对应位置：

- `src/main/resources/app-public.yml:48`
- `src/main/resources/app-public.yml:82`

## 2. 启动方式

### 低配服务器

```powershell
java -jar app.jar --spring.profiles.active=server-low
```

适用：

- 2 核 / 2G
- 并发峰值控制在 300~800 左右
- 学校错峰开启抢课

### 高配服务器

```powershell
java -jar app.jar --spring.profiles.active=server-high
```

适用：

- 4 核 / 8G
- 并发峰值 800~1000 左右
- 多个班级单元同时进入第二轮

### 与本地配置叠加

```powershell
java -jar app.jar --spring.profiles.active=local,server-high
```

## 3. 当前应用档位参数

### `server-low`

- Hikari 连接池：`maximum-pool-size=12`
- Spring 异步线程池：`core-size=4`，`max-size=8`，`queue-capacity=120`
- Tomcat：`threads.max=100`，`accept-count=300`，`max-connections=1000`

### `server-high`

- Hikari 连接池：`maximum-pool-size=24`
- Spring 异步线程池：`core-size=8`，`max-size=16`，`queue-capacity=300`
- Tomcat：`threads.max=180`，`accept-count=800`，`max-connections=2000`

## 4. 抢课专用 JVM 参数建议

### 低配服务器（2C2G）

```powershell
java `
  -Xms1024m -Xmx1024m `
  -XX:+UseG1GC `
  -XX:MaxGCPauseMillis=200 `
  -XX:+HeapDumpOnOutOfMemoryError `
  -XX:HeapDumpPath=./logs `
  -Dfile.encoding=UTF-8 `
  -jar app.jar `
  --spring.profiles.active=server-low
```

### 高配服务器（4C8G）

```powershell
java `
  -Xms3072m -Xmx3072m `
  -XX:+UseG1GC `
  -XX:MaxGCPauseMillis=200 `
  -XX:+HeapDumpOnOutOfMemoryError `
  -XX:HeapDumpPath=./logs `
  -Dfile.encoding=UTF-8 `
  -jar app.jar `
  --spring.profiles.active=server-high
```

## 5. MySQL 参数建议

以下为抢课高峰期推荐起点值，具体仍需结合你的实例规格调整：

```ini
[mysqld]
max_connections = 300
innodb_buffer_pool_size = 1G
innodb_buffer_pool_instances = 1
innodb_log_file_size = 256M
innodb_flush_log_at_trx_commit = 1
sync_binlog = 1
thread_cache_size = 64
table_open_cache = 1024
tmp_table_size = 64M
max_heap_table_size = 64M
```

说明：

- 如果数据库与应用部署在同一台 2G 机器，不建议把 `innodb_buffer_pool_size` 设太大
- 若使用 4C8G 且 MySQL 独立部署，可把 `innodb_buffer_pool_size` 调到 `2G~4G`
- 抢课场景优先保证事务一致性，不建议为了性能关闭关键持久化参数

## 6. 运维建议

### 抢课前

- 提前 30 分钟重启应用，清理历史堆积连接
- 检查数据库连接数、磁盘空间、CPU 和内存
- 关闭无关的大批量导入、备份、统计任务
- 用 `scripts/regression/` 或 JMeter 先做一轮预热验证

### 抢课中

- 重点盯：
  - CPU
  - JVM 堆使用率
  - 数据库连接数
  - Tomcat 活跃线程数
  - 5xx / 超时数量
- 如学校量级较大，优先采用“按抢课单元错峰开启”

### 抢课后

- 导出回归结果与日志
- 核对管理端、教师端、报名明细页统计口径
- 抽样检查是否存在超卖、漏计、重复确认

## 7. 推荐执行策略

### 低配环境

- 采用 `server-low`
- 班级单元错峰开启
- 峰值尽量控制在 500~800 内

### 高配环境

- 采用 `server-high`
- 允许更多班级同时开启
- 峰值控制在 800~1000 更稳妥

## 8. 相关文档

- 待办清单：`course-selection-remaining-todo.md:1`
- 上线手册：`course-selection-operations-manual.md:1`
- 回归说明：`scripts/jmeter/README-course-selection-regression.md:1`
- 回归工具：`scripts/regression/README.md:1`
