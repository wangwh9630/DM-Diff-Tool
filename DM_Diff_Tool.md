# DM-Diff 达梦数据库结构对比工具 — 规划书

## 1. 项目定位
对比两个达梦数据库（源库/旧库 与 目标库/新库）之间的结构差异，自动生成 DDL 升级脚本并支持在线执行。同时提供数据迁移、触发器比较、数据对比、报告导出等一站式数据库升级迁移能力。

## 2. 运行环境与技术栈
- **运行环境**：Java 21 + (内置绿色版 JRE 21 可免安装运行)
- **目标数据库**：达梦数据库（DM8）
- **后端框架**：Spring Boot 3.2.0 + Thymeleaf + JDBC API
- **前端框架**：Bootstrap 5 + Chart.js + SSE (Server-Sent Events)
- **构建工具**：Maven
- **JDBC 驱动**：DmJdbcDriver8.jar

## 3. 已实现功能清单

### 3.1 连接管理
- 配置源库（旧库）与目标库（新库）：主机、端口、数据库名、用户名、密码
- 一键测试连接，回显数据库版本及大小写敏感参数
- 下拉选择全部模式（Schema），支持手动输入
- 外部配置文件 `application.yml` 持久化连接配置
- 保存/加载比对配置为 JSON 文件
- 加载指示器和详细错误提示

### 3.2 全量看板
- 展示源库/目标库的表、视图、索引、存储过程总数
- 显示变化量（+/- 差值）
- 差异统计图表（饼图 + 柱状图）
- 一键进入结构比对

### 3.3 结构比对
- **表比对**：新增表 / 删除表 / 修改表
- **字段比对**：新增字段、删除字段、字段类型/长度/精度/是否为空/默认值变化
- **索引比对**：新增索引、删除索引、修改索引（支持唯一索引、复合索引）
- **比对选项**：
  - 忽略大小写
  - 表名黑名单（支持正则和通配符）
  - 可勾选比对范围：表 / 视图 / 索引 / 存储过程
- 并排 DDL 比较视图（左右对比源/目标的完整 DDL，差异行高亮）

### 3.4 SQL 脚本生成
- 正向升级 SQL（CREATE TABLE / CREATE INDEX / ALTER TABLE / DROP TABLE / DROP INDEX）
- 逆向回滚 SQL（反向操作，用于撤销升级）
- 遵循 DDL 执行顺序（先删索引 → 删字段 → 增字段 → 改字段 → 增索引）
- 所有表名/索引名带 Schema 前缀
- 一键复制 SQL

### 3.5 SQL 在线执行
- **逐类执行**：分别执行新增表、修改表、删除表
- **全部执行**：一键执行全部升级 SQL
- **SSE 实时进度**：执行进度条 + 逐条状态推送
- **回滚执行**：独立回滚新增（DROP）、回滚修改（反转ALTER）、回滚删除（重建表）
- **遇错继续**：可勾选，单条失败不影响后续执行
- **执行日志**：每条语句的执行结果（表名、操作类型、成功/失败、错误信息）
- **多线程并行**：不同表的 DDL 并行执行（5 线程池）

### 3.6 快照回滚
- 执行前自动创建快照（保存回滚语句）
- 支持通过快照 ID 一键回滚到特定状态
- 快照存储在内存中（ConcurrentHashMap）

### 3.7 UI/UX
- **夜间模式**：全站深色主题，持久化到 localStorage
- **固定操作栏**：比对结果页顶部固定统计和操作按钮
- **可折叠卡片**：每张差异表折叠/展开，支持全部展开/收起
- **差异统计图表**：饼图 + 柱状图
- **移动端适配**：响应式布局

### 3.8 安全与容错
- SQL 注入防护：PreparedStatement 参数化查询
- 复合索引正确处理：按索引名分组
- 日志记录：每次执行记录目标库、语句和结果
- 所有 SQL 执行在旧库（源库）上，新库（目标库）仅作参照

### 3.9 数据迁移
- **迁移策略**：先表结构（DDL），再表数据（DML），确保顺序正确
- **指定表选择**：按表名勾选需迁移的表，支持全选/取消全选/仅选目标库不存在表
- **表列表展示**：显示每张表的行数和目标库存在状态
- **搜索过滤**：按表名关键词搜索
- **DDL迁移**：通过 `DBMS_METADATA.GET_DDL` 获取完整建表语句，自动替换模式名
- **数据迁移**：分批读取源库数据（可配置批次大小 100~10000），批量 PreparedStatement 写入目标库
- **约束处理**：迁移数据前自动禁用目标表约束，迁移完成后重新启用
- **SSE 实时进度**：结构/数据分阶段进度条，实时日志展示每张表的迁移状态

### 3.10 触发器比较
- 自动获取源库和目标库的所有触发器信息
- 检测新增触发器、删除触发器、修改触发器三类差异
- 对比维度：触发器名、关联表、触发类型、触发事件、状态、触发体内容
- 支持查看单个触发器的完整 DDL

### 3.11 数据对比
- 对比源库和目标库所有共有表的行数差异
- 展示每张表的源库行数、目标库行数、差异数量、匹配状态
- 支持查看单张表的数据样本对比（前 N 行数据内容）
- 支持导出 CSV 格式的数据对比报告

### 3.12 导出报告
- **HTML 报告**：导出结构比对结果的完整 HTML 报告，包含差异摘要、新增/删除/修改表详情
- **CSV 报告**：导出数据对比结果的 CSV 文件，便于 Excel 打开分析

## 4. 项目结构

```
dm-diff-tool/
├── src/main/java/com/example/dmdiff/
│   ├── DmDiffToolApplication.java          # 启动类
│   ├── config/DmDiffConfig.java            # 外部配置映射
│   ├── controller/DmDiffController.java    # 所有控制器
│   ├── dto/                                # 数据传输对象
│   │   ├── ColumnDiff.java                 # 字段差异
│   │   ├── ColumnInfo.java                 # 字段信息
│   │   ├── ConnectionConfig.java           # 连接配置
│   │   ├── ConnectionResult.java           # 连接测试结果
│   │   ├── DiffConfig.java                # 比对配置
│   │   ├── DiffResult.java                 # 比对结果
│   │   ├── IndexDiff.java                  # 索引差异
│   │   ├── IndexInfo.java                  # 索引信息
│   │   ├── MigrationProgress.java          # 迁移进度DTO（新增）
│   │   ├── MigrationRequest.java           # 迁移请求DTO（新增）
│   │   ├── ProgressEvent.java              # SSE进度事件
│   │   ├── SqlStatement.java               # SQL语句封装
│   │   ├── TableDiff.java                  # 表差异
│   │   └── TableInfo.java                  # 表信息
│   ├── diff/DiffType.java                  # 差异类型枚举
│   └── service/
│       ├── DatabaseService.java            # 数据库操作
│       ├── DiffService.java                # 比对逻辑
│       ├── MigrationService.java           # 数据迁移服务（新增）
│       └── SqlGeneratorService.java        # SQL生成
├── src/main/resources/templates/
│   ├── dashboard.html                      # 全量看板
│   ├── diff.html                           # 比对结果页
│   ├── index.html                          # 连接配置页
│   ├── migration.html                      # 数据迁移页（新增）
│   └── sql.html                            # SQL脚本与执行页
├── application.yml                         # 外部配置
├── pom.xml                                 # Maven构建
└── README.md                               # 使用说明
```

## 5. 开发中注意事项

### 达梦数据库兼容
- 使用 JDBC `DatabaseMetaData` API 获取元数据（而非 DM 系统视图 `ALL_TABLES` 等）
- DDL 语法使用 DM 兼容格式：
  - `ALTER TABLE t ADD col type`（不是 `ADD COLUMN`）
  - `ALTER TABLE t MODIFY col type`（不是 `MODIFY COLUMN`）
  - `DROP INDEX schema.idx_name`（索引名必须带 Schema 前缀）
- `DBMS_METADATA.GET_DDL` 可能不可用，需要捕获异常
- 复合索引通过 `getIndexInfo()` 返回多行，需按索引名分组

### 执行方向
- 源库（sourceConfig）= 旧库 = SQL 执行目标
- 目标库（targetConfig）= 新库 = 参照标准
- 所有升级 SQL 在源库执行，将新库的变更同步到旧库
- 生成 SQL 时 Schema 使用旧库的 schema 名

### 前端
- SSE (`EventSource`) 用于实时进度推送，只能发 GET 请求
- 页面不 reload（已去除 `location.reload()`），日志持久保留
- 夜间模式通过 `data-bs-theme` 属性切换

## 6. 后续可优化方向
- ~~数据差异比对（行数/数据内容）~~ ✅ 已实现
- ~~触发器、外键约束比对~~ ✅ 已实现（触发器） ❌ 待实现（外键约束）
- DDL 脚本手动编辑调整
- ~~导出比对报告（HTML/PDF）~~ ✅ 已实现（HTML/CSV）
- 定时自动比对同步
- 异构数据库支持（DM → MySQL 等）
- 选择性数据迁移（按条件过滤数据行）
- 视图/存储过程/函数迁移
- 数据校验（逐行校验数据一致性）