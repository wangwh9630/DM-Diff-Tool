# DM-Diff 达梦数据库结构对比与数据迁移工具

## 1. 项目简介

DM-Diff 是一款基于 Spring Boot 的达梦数据库结构对比与数据迁移工具，用于对比两个达梦数据库（源库/旧库 与 目标库/新库）之间的结构差异，自动生成 DDL 升级脚本并支持在线执行，同时提供数据迁移、触发器比较、数据对比、报告导出等一站式数据库升级迁移能力。

## 2. 运行环境与技术栈

- **运行环境**：Java 21 + (内置绿色版 JRE 21 可免安装运行)
- **目标数据库**：达梦数据库 DM8
- **后端框架**：Spring Boot 3.2.0 + Thymeleaf
- **前端框架**：Bootstrap 5 + Chart.js + SSE
- **构建工具**：Maven
- **JDBC 驱动**：DmJdbcDriver8.jar

## 3. 已实现功能

### 3.1 连接管理
- 支持配置源库（旧库）与目标库（新库）连接（主机、端口、用户名、密码、数据库名）
- 支持一键测试连接，回显数据库版本及大小写敏感参数
- 支持下拉选择模式（Schema），也可手动输入
- 支持外部配置文件 `application.yml` 持久化连接配置
- 支持加载/保存比对配置为 JSON 文件

### 3.2 全量看板
- 比对前展示双方表、视图、索引、存储过程的总数/变化量
- 差异统计图表（饼图 + 柱状图展示新增/删除/修改分布）
- 一键进入结构比对

### 3.3 结构比对
- **表比对**：新增表 / 删除表 / 修改表
- **字段比对**：新增字段、删除字段、字段类型/长度/精度/是否为空/默认值变化
- **索引比对**：新增索引、删除索引、修改索引（含唯一索引、复合索引）
- **比对选项**：
  - 忽略大小写
  - 表名黑名单过滤
  - 可勾选比对范围（表/视图/索引/存储过程）

### 3.4 SQL 脚本生成
- 自动生成正向升级 SQL（CREATE / ALTER / DROP）
- 自动生成逆向回滚 SQL（反向操作）
- 并排 DDL 比较视图（新库 vs 旧库 DDL 差异）
- 一键复制 SQL

### 3.5 SQL 在线执行
- **逐类执行**：分别执行新增表、修改表、删除表
- **全部执行**：一键执行所有升级 SQL，支持 SSE 实时进度推送
- **回滚执行**：独立回滚新增、回滚修改、回滚删除，支持快照回滚
- **遇错继续**：可勾选"遇到错误继续"，单条失败不影响后续
- **执行日志**：每条语句的执行状态（成功/失败 + 错误信息）实时显示
- **进度条**：执行过程中显示 N/M 进度和百分比
- **多线程并行**：不同表之间的 DDL 可并行执行（5 线程池）

### 3.6 UI/UX 优化
- **夜间模式**：全站支持深色主题，切换持久化到 localStorage
- **固定操作栏**：比对结果页顶部固定差异统计和操作按钮
- **可折叠卡片**：每张差异表可折叠/展开，支持全部展开/收起
- **差异统计图表**：饼图展示差异类型分布，柱状图展示数量统计
- **移动端适配**：响应式布局

### 3.7 安全与容错
- SQL 注入防护：所有数据库查询使用 PreparedStatement 参数化
- 复合索引正确处理：按索引名分组，不拆分为多个单列索引
- 日志记录：详细记录每次执行的目标库、语句内容和结果

### 3.8 数据迁移
- **迁移策略**：先迁移表结构（DDL），再迁移表数据（DML），确保顺序正确
- **指定表选择**：支持按表名勾选需要迁移的表，提供全选/取消全选/仅选目标库不存在表
- **表列表展示**：显示每张表的行数和在目标库中的存在状态
- **搜索过滤**：支持按表名关键词搜索
- **DDL迁移**：通过 `DBMS_METADATA.GET_DDL` 获取完整建表语句，自动替换模式名
- **数据迁移**：分批读取源库数据（可配置批次大小 100~10000），批量 PreparedStatement 写入目标库
- **约束处理**：迁移数据前自动禁用目标表约束，迁移完成后重新启用
- **SSE 实时进度**：展示结构/数据分阶段进度条，实时日志展示每张表的迁移状态

### 3.9 触发器比较
- 自动获取源库和目标库的所有触发器信息
- 检测新增触发器、删除触发器、修改触发器三类差异
- 对比维度：触发器名、关联表、触发类型、触发事件、状态、触发体内容
- 支持查看单个触发器的完整 DDL

### 3.10 数据对比
- 对比源库和目标库所有共有表的行数差异
- 展示每张表的源库行数、目标库行数、差异数量、匹配状态
- 支持查看单张表的数据样本对比（前 N 行数据内容）
- 支持导出 CSV 格式的数据对比报告

### 3.11 导出报告
- **HTML 报告**：导出结构比对结果的完整 HTML 报告，包含差异摘要、新增/删除/修改表详情
- **CSV 报告**：导出数据对比结果的 CSV 文件，便于 Excel 打开分析

## 4. 项目结构

```
dm-diff-tool/
├── src/main/java/com/example/dmdiff/
│   ├── DmDiffToolApplication.java          # 启动类
│   ├── config/
│   │   └── DmDiffConfig.java              # 外部配置映射
│   ├── controller/
│   │   └── DmDiffController.java          # 所有控制器
│   ├── dto/
│   │   ├── ColumnDiff.java                # 字段差异
│   │   ├── ColumnInfo.java                # 字段信息
│   │   ├── ConnectionConfig.java          # 连接配置
│   │   ├── ConnectionResult.java          # 连接测试结果
│   │   ├── DiffConfig.java               # 比对配置
│   │   ├── DiffResult.java                # 比对结果
│   │   ├── IndexDiff.java                 # 索引差异
│   │   ├── IndexInfo.java                 # 索引信息
│   │   ├── MigrationProgress.java         # 迁移进度DTO（新增）
│   │   ├── MigrationRequest.java          # 迁移请求DTO（新增）
│   │   ├── ProgressEvent.java             # SSE进度事件
│   │   ├── SqlStatement.java              # SQL语句封装
│   │   ├── TableDiff.java                 # 表差异
│   │   └── TableInfo.java                 # 表信息
│   ├── diff/
│   │   └── DiffType.java                  # 差异类型枚举
│   └── service/
│       ├── DatabaseService.java           # 数据库操作
│       ├── DiffService.java               # 比对逻辑
│       ├── MigrationService.java          # 数据迁移服务（新增）
│       └── SqlGeneratorService.java       # SQL生成
├── src/main/resources/templates/
│   ├── dashboard.html                     # 全量看板
│   ├── diff.html                          # 比对结果页
│   ├── index.html                         # 连接配置页
│   ├── migration.html                     # 数据迁移页（新增）
│   └── sql.html                           # SQL脚本与执行页
├── application.yml                        # 外部配置
├── pom.xml                                # Maven构建
└── DM_Diff_Tool.md                        # 规划书
```

## 5. 快速开始

### 5.1 配置连接
编辑 `application.yml`：
```yaml
dm-diff:
  source:
    host: 192.168.1.100
    port: 5236
    database: OLD_DB
    username: SYSDBA
    password: SYSDBA
  target:
    host: 192.168.1.101
    port: 5236
    database: NEW_DB
    username: SYSDBA
    password: SYSDBA
```

### 5.2 启动
```bash
java -jar dm-diff-tool.jar
```
访问 `http://localhost:8080`

### 5.3 使用流程
1. 在首页配置源库（旧库）和目标库（新库）连接
2. 选择对应模式（Schema）
3. 点击「进入全量看板」查看概览
4. 点击「开始比对」查看结构差异
5. 点击「生成SQL脚本」查看DDL
6. 逐类执行或全部执行升级 SQL
7. 如需回滚，点击对应回滚按钮

### 5.4 数据迁移流程
1. 在全量看板或比对结果页点击「数据迁移」
2. 在表列表中勾选需要迁移的表（支持全选/搜索过滤）
3. 选择迁移选项：表结构迁移、表数据迁移、批次大小
4. 点击「开始迁移」，实时查看 SSE 进度和日志
5. 等待迁移完成后，可切换到数据对比验证一致性

### 5.5 触发器对比
1. 在 SQL 脚本页面点击「触发器对比」按钮
2. 弹出模态框展示源库和目标库的触发器差异
3. 支持查看新增/删除/修改三类触发器详情

### 5.6 数据对比
1. 在 SQL 脚本页面点击「数据对比」按钮
2. 弹出模态框展示所有共有表的行数对比结果
3. 点击「导出CSV」下载数据对比报告

### 5.7 导出报告
1. 在 SQL 脚本页面点击「导出HTML报告」下载结构对比报告
2. 在数据对比模态框点击「导出CSV」下载数据对比报告

## 6. 开发指南

### 构建打包
```bash
mvn clean package -DskipTests
```
生成 `target/dm-diff-tool-1.0.0.jar`

### 达梦数据库注意事项
- 使用 JDBC `DatabaseMetaData` API 获取元数据，而非 DM 系统视图
- DDL 语法需使用 DM 兼容格式（`ALTER TABLE t ADD col` 而非 `ADD COLUMN`）
- 表和索引名需带 Schema 前缀（`DROP INDEX SCHEMA.IDX_NAME`）
- 索引对比需按索引名分组处理复合索引