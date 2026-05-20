package com.example.dmdiff.controller;

import com.example.dmdiff.config.DmDiffConfig;
import com.example.dmdiff.diff.DiffResult;
import com.example.dmdiff.dto.ConnectionConfig;
import com.example.dmdiff.dto.ConnectionResult;
import com.example.dmdiff.dto.DiffConfig;
import com.example.dmdiff.dto.MigrationProgress;
import com.example.dmdiff.dto.MigrationRequest;
import com.example.dmdiff.dto.SqlStatement;
import com.example.dmdiff.metadata.ColumnInfo;
import com.example.dmdiff.metadata.IndexInfo;
import com.example.dmdiff.metadata.TableInfo;
import com.example.dmdiff.service.DatabaseService;
import com.example.dmdiff.service.DiffService;
import com.example.dmdiff.service.MigrationService;
import com.example.dmdiff.service.SqlGeneratorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Controller
public class DmDiffController {
    private static final Logger logger = LoggerFactory.getLogger(DmDiffController.class);

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private DiffService diffService;

    @Autowired
    private SqlGeneratorService sqlGeneratorService;

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private DmDiffConfig dmDiffConfig;

    @Autowired
    private TaskExecutor taskExecutor;

    private ConnectionConfig sourceConfig = new ConnectionConfig();
    private ConnectionConfig targetConfig = new ConnectionConfig();
    private DiffResult currentDiffResult;
    private String lastSnapshotId;
    private Map<String, List<SqlStatement>> snapshots = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        DmDiffConfig.DatabaseConfig sourceDb = dmDiffConfig.getSource();
        sourceConfig.setHost(sourceDb.getHost());
        sourceConfig.setPort(sourceDb.getPort());
        sourceConfig.setDatabase(sourceDb.getDatabase());
        sourceConfig.setUsername(sourceDb.getUsername());
        sourceConfig.setPassword(sourceDb.getPassword());

        DmDiffConfig.DatabaseConfig targetDb = dmDiffConfig.getTarget();
        targetConfig.setHost(targetDb.getHost());
        targetConfig.setPort(targetDb.getPort());
        targetConfig.setDatabase(targetDb.getDatabase());
        targetConfig.setUsername(targetDb.getUsername());
        targetConfig.setPassword(targetDb.getPassword());

        File configsDir = new File("configs");
        if (!configsDir.exists()) {
            configsDir.mkdirs();
        }
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("sourceConfig", sourceConfig);
        model.addAttribute("targetConfig", targetConfig);
        return "index";
    }

    @PostMapping("/test-source")
    @ResponseBody
    public ConnectionResult testSourceConnection(@RequestBody ConnectionConfig config) {
        sourceConfig = config;
        return databaseService.testConnection(config);
    }

    @PostMapping("/test-target")
    @ResponseBody
    public ConnectionResult testTargetConnection(@RequestBody ConnectionConfig config) {
        targetConfig = config;
        return databaseService.testConnection(config);
    }

    @PostMapping("/get-databases")
    @ResponseBody
    public List<String> getDatabases(@RequestBody ConnectionConfig config) {
        try {
            return databaseService.getDatabases(config);
        } catch (SQLException e) {
            logger.error("获取数据库列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        try {
            Map<String, Object> sourceStats = getDatabaseStats(sourceConfig);
            Map<String, Object> targetStats = getDatabaseStats(targetConfig);

            model.addAttribute("sourceStats", sourceStats);
            model.addAttribute("targetStats", targetStats);
            model.addAttribute("sourceConfig", sourceConfig);
            model.addAttribute("targetConfig", targetConfig);

            return "dashboard";
        } catch (SQLException e) {
            logger.error("获取数据库统计信息失败: {}", e.getMessage());
            model.addAttribute("error", "获取数据库统计信息失败: " + e.getMessage());
            return "error";
        }
    }

    private Map<String, Object> getDatabaseStats(ConnectionConfig config) throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        stats.put("tableCount", databaseService.getTableCount(config));
        stats.put("viewCount", databaseService.getViewCount(config));
        stats.put("indexCount", databaseService.getIndexCount(config));
        stats.put("procedureCount", databaseService.getProcedureCount(config));
        return stats;
    }

    @GetMapping("/diff")
    public String diff(Model model,
                       @RequestParam(defaultValue = "false") boolean ignoreCase,
                       @RequestParam(required = false) List<String> blacklist,
                       @RequestParam(defaultValue = "true") boolean compareTables,
                       @RequestParam(defaultValue = "false") boolean compareViews,
                       @RequestParam(defaultValue = "false") boolean compareIndexes,
                       @RequestParam(defaultValue = "false") boolean compareProcedures) {
        try {
            DiffConfig diffConfig = new DiffConfig();
            diffConfig.setSourceConfig(sourceConfig);
            diffConfig.setTargetConfig(targetConfig);
            diffConfig.setIgnoreCase(ignoreCase);
            diffConfig.setBlacklist(blacklist);
            diffConfig.setCompareTables(compareTables);
            diffConfig.setCompareViews(compareViews);
            diffConfig.setCompareIndexes(compareIndexes);
            diffConfig.setCompareProcedures(compareProcedures);

            logger.info("开始比对：source(database={}) vs target(database={})", 
                sourceConfig.getDatabase(), targetConfig.getDatabase());
            currentDiffResult = diffService.compare(diffConfig);

            model.addAttribute("diffResult", currentDiffResult);
            model.addAttribute("ignoreCase", ignoreCase);
            model.addAttribute("blacklist", blacklist);
            model.addAttribute("compareTables", compareTables);
            model.addAttribute("compareViews", compareViews);
            model.addAttribute("compareIndexes", compareIndexes);
            model.addAttribute("compareProcedures", compareProcedures);

            return "diff";
        } catch (SQLException e) {
            logger.error("比对失败: {}", e.getMessage());
            model.addAttribute("error", "比对失败: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/sql")
    public String sql(Model model) {
        if (currentDiffResult == null) {
            model.addAttribute("error", "请先执行数据库比对");
            return "error";
        }

        String sourceSchema = sourceConfig.getDatabase();
        String upgradeSql = sqlGeneratorService.generateUpgradeSql(currentDiffResult, sourceSchema);
        String rollbackSql = sqlGeneratorService.generateRollbackSql(currentDiffResult, sourceSchema);

        model.addAttribute("upgradeSql", upgradeSql);
        model.addAttribute("rollbackSql", rollbackSql);
        model.addAttribute("sourceConfig", sourceConfig);
        model.addAttribute("targetConfig", targetConfig);

        logger.info("SQL页面：sourceConfig(database={}), targetConfig(database={})", 
            sourceConfig.getDatabase(), targetConfig.getDatabase());

        return "sql";
    }

    // ==================== 进度事件 DTO ====================

    public static class ProgressEvent {
        private int total;
        private int completed;
        private String tableName;
        private String actionType;
        private boolean success;
        private String errorMessage;
        private boolean done;

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getCompleted() { return completed; }
        public void setCompleted(int completed) { this.completed = completed; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
    }

    // ==================== 顺序执行 ====================

    private List<SqlStatement> executeStatements(List<SqlStatement> statements) {
        logger.info("executeStatements: 开始执行 {} 条语句，目标旧库={}", statements.size(), sourceConfig.getDatabase());
        for (SqlStatement stmt : statements) {
            try {
                databaseService.executeSql(sourceConfig, stmt.getSql());
                stmt.setSuccess(true);
                logger.info("SQL执行成功 [{}] {}: {}", stmt.getActionType(), stmt.getTableName(), stmt.getSql());
            } catch (SQLException e) {
                stmt.setSuccess(false);
                stmt.setErrorMessage(e.getMessage());
                logger.error("SQL执行失败 [{}] {}: {}", stmt.getActionType(), stmt.getTableName(), e.getMessage());
            }
        }
        logger.info("executeStatements: 执行完成，成功/失败={}", 
            statements.stream().filter(SqlStatement::isSuccess).count() + "/" + 
            statements.stream().filter(s -> !s.isSuccess()).count());
        return statements;
    }

    // ==================== 多线程并行执行 (#11) ====================

    private void executeStatementsParallel(List<SqlStatement> statements, SseEmitter emitter) {
        Map<String, List<SqlStatement>> grouped = statements.stream()
                .collect(Collectors.groupingBy(SqlStatement::getTableName, LinkedHashMap::new, Collectors.toList()));

        int total = statements.size();
        AtomicInteger completed = new AtomicInteger(0);

        if (emitter != null) {
            try {
                ProgressEvent initialEvent = new ProgressEvent();
                initialEvent.setTotal(total);
                initialEvent.setCompleted(0);
                initialEvent.setDone(false);
                emitter.send(initialEvent);
            } catch (IOException e) {
                logger.error("发送SSE初始事件失败", e);
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(grouped.size());

        for (List<SqlStatement> tableStatements : grouped.values()) {
            executor.submit(() -> {
                try {
                    for (SqlStatement stmt : tableStatements) {
                        try {
                            databaseService.executeSql(sourceConfig, stmt.getSql());
                            stmt.setSuccess(true);
                        } catch (SQLException e) {
                            stmt.setSuccess(false);
                            stmt.setErrorMessage(e.getMessage());
                            logger.error("SQL执行失败 [{}] {}: {}", stmt.getActionType(), stmt.getTableName(), e.getMessage());
                        }

                        int current = completed.incrementAndGet();
                        if (emitter != null) {
                            try {
                                ProgressEvent event = new ProgressEvent();
                                event.setTotal(total);
                                event.setCompleted(current);
                                event.setTableName(stmt.getTableName());
                                event.setActionType(stmt.getActionType());
                                event.setSuccess(stmt.isSuccess());
                                event.setErrorMessage(stmt.getErrorMessage());
                                event.setDone(false);
                                emitter.send(event);
                            } catch (IOException e) {
                                logger.error("发送SSE进度事件失败", e);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.shutdown();

        if (emitter != null) {
            try {
                ProgressEvent doneEvent = new ProgressEvent();
                doneEvent.setTotal(total);
                doneEvent.setCompleted(total);
                doneEvent.setDone(true);
                emitter.send(doneEvent);
            } catch (IOException e) {
                logger.error("发送SSE完成事件失败", e);
            } finally {
                emitter.complete();
            }
        }
    }

    // ==================== 执行端点 ====================

    @PostMapping("/execute-create")
    @ResponseBody
    public List<SqlStatement> executeCreate() {
        if (currentDiffResult == null) {
            return new ArrayList<>();
        }
        String sourceSchema = sourceConfig.getDatabase();
        return executeStatements(sqlGeneratorService.generateCreate(currentDiffResult, sourceSchema));
    }

    @PostMapping("/execute-alter")
    @ResponseBody
    public List<SqlStatement> executeAlter() {
        if (currentDiffResult == null) {
            return new ArrayList<>();
        }
        String sourceSchema = sourceConfig.getDatabase();
        return executeStatements(sqlGeneratorService.generateAlter(currentDiffResult, sourceSchema));
    }

    @PostMapping("/execute-drop")
    @ResponseBody
    public List<SqlStatement> executeDrop() {
        if (currentDiffResult == null) {
            return new ArrayList<>();
        }
        String sourceSchema = sourceConfig.getDatabase();
        return executeStatements(sqlGeneratorService.generateDrop(currentDiffResult, sourceSchema));
    }

    @PostMapping("/rollback-added")
    @ResponseBody
    public List<SqlStatement> rollbackAdded() {
        if (currentDiffResult == null) {
            return new ArrayList<>();
        }
        String sourceSchema = sourceConfig.getDatabase();
        return executeStatements(sqlGeneratorService.rollbackAdded(currentDiffResult, sourceSchema));
    }

    @PostMapping("/rollback-modified")
    @ResponseBody
    public List<SqlStatement> rollbackModified() {
        if (currentDiffResult == null) {
            return new ArrayList<>();
        }
        String sourceSchema = sourceConfig.getDatabase();
        return executeStatements(sqlGeneratorService.rollbackModified(currentDiffResult, sourceSchema));
    }

    @PostMapping("/rollback-deleted")
    @ResponseBody
    public List<SqlStatement> rollbackDeleted() {
        if (currentDiffResult == null) {
            return new ArrayList<>();
        }
        String sourceSchema = sourceConfig.getDatabase();
        return executeStatements(sqlGeneratorService.rollbackDeleted(currentDiffResult, sourceSchema));
    }

    @PostMapping("/execute-all")
    @ResponseBody
    public Map<String, Object> executeAll(@RequestParam String sqlType) {
        Map<String, Object> result = new HashMap<>();
        if (currentDiffResult == null) {
            result.put("success", false);
            result.put("message", "请先执行比对");
            return result;
        }
        
        List<SqlStatement> allStatements = new ArrayList<>();
        String sourceSchema = sourceConfig.getDatabase();
        
        if ("upgrade".equalsIgnoreCase(sqlType)) {
            allStatements = sqlGeneratorService.generateCreate(currentDiffResult, sourceSchema);
            allStatements.addAll(sqlGeneratorService.generateAlter(currentDiffResult, sourceSchema));
            allStatements.addAll(sqlGeneratorService.generateDrop(currentDiffResult, sourceSchema));
        } else if ("rollback".equalsIgnoreCase(sqlType)) {
            allStatements = sqlGeneratorService.rollbackAll(currentDiffResult, sourceSchema);
        }

        executeStatements(allStatements);
        
        result.put("success", true);
        result.put("statements", allStatements);
        result.put("snapshotId", lastSnapshotId);
        
        return result;
    }

    // ==================== SSE 执行进度流 (#4) ====================

    @GetMapping(value = "/execute-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeStream(@RequestParam String sqlType) {
        SseEmitter emitter = new SseEmitter(600000L);

        if (currentDiffResult == null) {
            try {
                ProgressEvent event = new ProgressEvent();
                event.setDone(true);
                event.setErrorMessage("请先执行数据库比对");
                emitter.send(event);
                emitter.complete();
            } catch (IOException e) {
                logger.error("SSE发送失败", e);
            }
            return emitter;
        }

        String sourceSchema = sourceConfig.getDatabase();
        lastSnapshotId = createSnapshot();

        List<SqlStatement> allStatements;
        if ("upgrade".equalsIgnoreCase(sqlType)) {
            allStatements = sqlGeneratorService.generateCreate(currentDiffResult, sourceSchema);
            allStatements.addAll(sqlGeneratorService.generateAlter(currentDiffResult, sourceSchema));
            allStatements.addAll(sqlGeneratorService.generateDrop(currentDiffResult, sourceSchema));
        } else if ("rollback".equalsIgnoreCase(sqlType)) {
            allStatements = sqlGeneratorService.rollbackAll(currentDiffResult, sourceSchema);
        } else {
            try {
                ProgressEvent event = new ProgressEvent();
                event.setDone(true);
                event.setErrorMessage("未知的 SQL 类型");
                emitter.send(event);
                emitter.complete();
            } catch (IOException e) {
                logger.error("SSE发送失败", e);
            }
            return emitter;
        }

        List<SqlStatement> finalStatements = allStatements;
        taskExecutor.execute(() -> {
            executeStatementsParallel(finalStatements, emitter);
        });

        return emitter;
    }

    // ==================== 保存/加载比对配置 (#3) ====================

    @PostMapping("/save-config")
    @ResponseBody
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            String name = (String) config.get("name");
            if (name == null || name.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "配置名称不能为空");
                return result;
            }

            File configsDir = new File("configs");
            if (!configsDir.exists()) {
                configsDir.mkdirs();
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File("configs/" + name + ".json"), config);

            result.put("success", true);
            result.put("message", "配置保存成功");
        } catch (IOException e) {
            logger.error("保存配置失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "保存配置失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/list-configs")
    @ResponseBody
    public List<String> listConfigs() {
        List<String> configs = new ArrayList<>();
        File configsDir = new File("configs");
        if (configsDir.exists()) {
            File[] files = configsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    configs.add(name.substring(0, name.length() - 5));
                }
            }
        }
        return configs;
    }

    @GetMapping("/load-config")
    @ResponseBody
    public Map<String, Object> loadConfig(@RequestParam String name) {
        Map<String, Object> result = new HashMap<>();
        try {
            File configFile = new File("configs/" + name + ".json");
            if (!configFile.exists()) {
                result.put("success", false);
                result.put("message", "配置不存在");
                return result;
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> config = mapper.readValue(configFile, new TypeReference<Map<String, Object>>() {});
            result.put("success", true);

            Object sourceConfig = config.get("sourceConfig");
            Object targetConfig = config.get("targetConfig");
            if (sourceConfig instanceof Map) {
                result.put("sourceConfig", sourceConfig);
            }
            if (targetConfig instanceof Map) {
                result.put("targetConfig", targetConfig);
            }
        } catch (IOException e) {
            logger.error("加载配置失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "加载配置失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 回滚快照 (#13) ====================

    private String createSnapshot() {
        if (currentDiffResult == null) {
            return null;
        }

        String snapshotId = UUID.randomUUID().toString();
        String sourceSchema = sourceConfig.getDatabase();
        List<SqlStatement> rollbackStatements = sqlGeneratorService.rollbackAll(currentDiffResult, sourceSchema);
        snapshots.put(snapshotId, rollbackStatements);
        logger.info("创建快照: {} ({} 条回滚语句)", snapshotId, rollbackStatements.size());
        return snapshotId;
    }

    @PostMapping("/rollback-snapshot")
    @ResponseBody
    public Map<String, Object> rollbackSnapshot(@RequestParam String snapshotId) {
        Map<String, Object> result = new HashMap<>();
        List<SqlStatement> rollbackStatements = snapshots.get(snapshotId);

        if (rollbackStatements == null) {
            result.put("success", false);
            result.put("message", "快照不存在或已过期");
            return result;
        }

        int successCount = 0;
        int failCount = 0;
        for (SqlStatement stmt : rollbackStatements) {
            try {
                databaseService.executeSql(sourceConfig, stmt.getSql());
                stmt.setSuccess(true);
                successCount++;
            } catch (SQLException e) {
                stmt.setSuccess(false);
                stmt.setErrorMessage(e.getMessage());
                failCount++;
                logger.error("回滚SQL执行失败 [{}] {}: {}", stmt.getActionType(), stmt.getTableName(), e.getMessage());
            }
        }

        result.put("success", failCount == 0);
        result.put("message", "回滚完成，成功 " + successCount + " 条，失败 " + failCount + " 条");
        result.put("statements", rollbackStatements);
        return result;
    }

    // ==================== 并排DDL比较视图 (#2) ====================

    @GetMapping("/get-table-ddl")
    @ResponseBody
    public Map<String, Object> getTableDdl(@RequestParam String tableName) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sourceDdl = getDdlFromConfig(sourceConfig, tableName);
            String targetDdl = getDdlFromConfig(targetConfig, tableName);
            result.put("sourceDdl", sourceDdl);
            result.put("targetDdl", targetDdl);
        } catch (SQLException e) {
            logger.error("获取表DDL失败: {}", e.getMessage());
            result.put("error", "获取表DDL失败: " + e.getMessage());
        }
        return result;
    }

    private String getDdlFromConfig(ConnectionConfig config, String tableName) throws SQLException {
        String schema = config.getDatabase();
        String simpleTableName = tableName;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            schema = parts[0];
            simpleTableName = parts[1];
        }

        String ddl = null;
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {

            String query = "SELECT DBMS_METADATA.GET_DDL('TABLE', '" + simpleTableName + "', '" + schema + "') FROM DUAL";
            try (ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    java.sql.Clob clob = rs.getClob(1);
                    if (clob != null) {
                        ddl = clob.getSubString(1, (int) clob.length());
                    }
                }
            }

            if (ddl == null || ddl.trim().isEmpty()) {
                String fallbackQuery = "SELECT DBMS_METADATA.GET_DDL('TABLE', '" + simpleTableName + "') FROM DUAL";
                try (ResultSet rs = stmt.executeQuery(fallbackQuery)) {
                    if (rs.next()) {
                        java.sql.Clob clob = rs.getClob(1);
                        if (clob != null) {
                            ddl = clob.getSubString(1, (int) clob.length());
                        }
                    }
                }
            }
        }

        return ddl != null ? ddl : "-- 无法获取DDL";
    }

    // ==================== 数据迁移 (#14) ====================

    @GetMapping("/migration")
    public String migration(Model model) {
        model.addAttribute("sourceConfig", sourceConfig);
        model.addAttribute("targetConfig", targetConfig);
        return "migration";
    }

    @GetMapping("/migration/tables")
    @ResponseBody
    public Map<String, Object> getMigrationTables() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> sourceTables = databaseService.getAllTableNames(sourceConfig);
            List<String> targetTables = databaseService.getAllTableNames(targetConfig);

            List<Map<String, Object>> tableList = new ArrayList<>();
            for (String tableName : sourceTables) {
                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("tableName", tableName);
                tableInfo.put("existsInTarget", targetTables.contains(tableName));
                try {
                    long rowCount = databaseService.getTableRowCount(sourceConfig, sourceConfig.getDatabase(), tableName);
                    tableInfo.put("rowCount", rowCount);
                } catch (SQLException e) {
                    tableInfo.put("rowCount", 0);
                }
                tableList.add(tableInfo);
            }

            result.put("success", true);
            result.put("tables", tableList);
        } catch (SQLException e) {
            logger.error("获取迁移表列表失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping(value = "/migration/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter migrateStream(@RequestParam(defaultValue = "") String tables,
                                     @RequestParam(defaultValue = "true") boolean structure,
                                     @RequestParam(defaultValue = "true") boolean data,
                                     @RequestParam(defaultValue = "1000") int batchSize) {
        SseEmitter emitter = new SseEmitter(600000L);

        List<String> tableNames = new ArrayList<>();
        if (tables != null && !tables.trim().isEmpty()) {
            for (String t : tables.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    tableNames.add(trimmed);
                }
            }
        } else {
            try {
                tableNames.addAll(databaseService.getAllTableNames(sourceConfig));
            } catch (SQLException e) {
                try {
                    MigrationProgress err = new MigrationProgress();
                    err.setDone(true);
                    err.setMessage("获取表列表失败: " + e.getMessage());
                    emitter.send(err);
                    emitter.complete();
                } catch (IOException ex) {
                    logger.error("SSE发送失败", ex);
                }
                return emitter;
            }
        }

        List<String> finalTableNames = tableNames;
        taskExecutor.execute(() -> {
            try {
                migrationService.migrateTables(sourceConfig, targetConfig, finalTableNames,
                        structure, data, batchSize, progress -> {
                            try {
                                emitter.send(progress);
                            } catch (IOException e) {
                                logger.error("SSE发送迁移进度失败", e);
                            }
                        });
            } catch (Exception e) {
                logger.error("迁移执行失败", e);
                try {
                    MigrationProgress err = new MigrationProgress();
                    err.setDone(true);
                    err.setMessage("迁移执行失败: " + e.getMessage());
                    emitter.send(err);
                } catch (IOException ex) {
                    logger.error("SSE发送错误事件失败", ex);
                }
            } finally {
                emitter.complete();
            }
        });

        return emitter;
    }

    // ==================== 触发器比较 (#15) ====================

    @GetMapping("/trigger-diff")
    @ResponseBody
    public Map<String, Object> getTriggerDiff() {
        Map<String, Object> result = new HashMap<>();
        try {
            String sourceSchema = sourceConfig.getDatabase();
            String targetSchema = targetConfig.getDatabase();

            List<Map<String, String>> sourceTriggers = databaseService.getTriggers(sourceConfig, sourceSchema);
            List<Map<String, String>> targetTriggers = databaseService.getTriggers(targetConfig, targetSchema);

            Map<String, Map<String, String>> sourceTriggerMap = new HashMap<>();
            Map<String, Map<String, String>> targetTriggerMap = new HashMap<>();

            for (Map<String, String> t : sourceTriggers) {
                sourceTriggerMap.put(t.get("triggerName"), t);
            }
            for (Map<String, String> t : targetTriggers) {
                targetTriggerMap.put(t.get("triggerName"), t);
            }

            List<Map<String, Object>> added = new ArrayList<>();
            List<Map<String, Object>> deleted = new ArrayList<>();
            List<Map<String, Object>> modified = new ArrayList<>();

            for (Map.Entry<String, Map<String, String>> entry : sourceTriggerMap.entrySet()) {
                String name = entry.getKey();
                if (!targetTriggerMap.containsKey(name)) {
                    Map<String, Object> diff = new HashMap<>();
                    diff.put("triggerName", name);
                    diff.put("sourceTrigger", entry.getValue());
                    added.add(diff);
                }
            }

            for (Map.Entry<String, Map<String, String>> entry : targetTriggerMap.entrySet()) {
                String name = entry.getKey();
                if (!sourceTriggerMap.containsKey(name)) {
                    Map<String, Object> diff = new HashMap<>();
                    diff.put("triggerName", name);
                    diff.put("targetTrigger", entry.getValue());
                    deleted.add(diff);
                } else {
                    Map<String, String> source = sourceTriggerMap.get(name);
                    Map<String, String> target = entry.getValue();
                    List<String> changes = new ArrayList<>();

                    if (!java.util.Objects.equals(source.get("tableName"), target.get("tableName")))
                        changes.add("关联表: " + source.get("tableName") + " -> " + target.get("tableName"));
                    if (!java.util.Objects.equals(source.get("triggerType"), target.get("triggerType")))
                        changes.add("类型: " + source.get("triggerType") + " -> " + target.get("triggerType"));
                    if (!java.util.Objects.equals(source.get("triggeringEvent"), target.get("triggeringEvent")))
                        changes.add("事件: " + source.get("triggeringEvent") + " -> " + target.get("triggeringEvent"));
                    if (!java.util.Objects.equals(source.get("status"), target.get("status")))
                        changes.add("状态: " + source.get("status") + " -> " + target.get("status"));
                    if (!java.util.Objects.equals(source.get("triggerBody"), target.get("triggerBody")))
                        changes.add("触发体内容不同");

                    if (!changes.isEmpty()) {
                        Map<String, Object> diff = new HashMap<>();
                        diff.put("triggerName", name);
                        diff.put("sourceTrigger", source);
                        diff.put("targetTrigger", target);
                        diff.put("changes", changes);
                        modified.add(diff);
                    }
                }
            }

            result.put("success", true);
            result.put("added", added);
            result.put("deleted", deleted);
            result.put("modified", modified);
            result.put("sourceCount", sourceTriggers.size());
            result.put("targetCount", targetTriggers.size());
        } catch (SQLException e) {
            logger.error("触发器对比失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/trigger-ddl")
    @ResponseBody
    public Map<String, Object> getTriggerDdl(@RequestParam String triggerName) {
        Map<String, Object> result = new HashMap<>();
        String sourceSchema = sourceConfig.getDatabase();
        String targetSchema = targetConfig.getDatabase();
        String sourceDdl = databaseService.getTriggerDdl(sourceConfig, sourceSchema, triggerName);
        String targetDdl = databaseService.getTriggerDdl(targetConfig, targetSchema, triggerName);
        result.put("sourceDdl", sourceDdl != null ? sourceDdl : "-- 源库无此触发器");
        result.put("targetDdl", targetDdl != null ? targetDdl : "-- 目标库无此触发器");
        return result;
    }

    // ==================== 数据对比 (#16) ====================

    @GetMapping("/data-compare")
    @ResponseBody
    public Map<String, Object> getDataCompare(@RequestParam(defaultValue = "") String tables) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sourceSchema = sourceConfig.getDatabase();
            String targetSchema = targetConfig.getDatabase();

            List<Map<String, Object>> comparisonList = new ArrayList<>();
            List<String> tableNames = new ArrayList<>();

            if (tables != null && !tables.trim().isEmpty()) {
                for (String t : tables.split(",")) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) tableNames.add(trimmed);
                }
            } else {
                List<String> sourceTableNames = databaseService.getAllTableNames(sourceConfig);
                List<String> targetTableNames = databaseService.getAllTableNames(targetConfig);
                for (String tn : sourceTableNames) {
                    if (targetTableNames.contains(tn)) {
                        tableNames.add(tn);
                    }
                }
            }

            for (String tableName : tableNames) {
                Map<String, Object> comparison = new HashMap<>();
                comparison.put("tableName", tableName);

                long sourceCount = databaseService.getTableRowCount(sourceConfig, sourceSchema, tableName);
                long targetCount = 0;
                try {
                    targetCount = databaseService.getTableRowCount(targetConfig, targetSchema, tableName);
                } catch (SQLException e) {
                    comparison.put("error", "目标表不存在或无法访问");
                }

                comparison.put("sourceCount", sourceCount);
                comparison.put("targetCount", targetCount);
                comparison.put("matched", sourceCount == targetCount);
                comparison.put("diffCount", Math.abs(sourceCount - targetCount));

                comparisonList.add(comparison);
            }

            result.put("success", true);
            result.put("comparisons", comparisonList);
        } catch (SQLException e) {
            logger.error("数据对比失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/data-compare/detail")
    @ResponseBody
    public Map<String, Object> getDataCompareDetail(@RequestParam String tableName,
                                                      @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sourceSchema = sourceConfig.getDatabase();
            String targetSchema = targetConfig.getDatabase();

            long sourceCount = databaseService.getTableRowCount(sourceConfig, sourceSchema, tableName);
            long targetCount = databaseService.getTableRowCount(targetConfig, targetSchema, tableName);

            result.put("tableName", tableName);
            result.put("sourceCount", sourceCount);
            result.put("targetCount", targetCount);

            List<Map<String, Object>> sourceSample = databaseService.getTableData(
                    sourceConfig, sourceSchema, tableName, Math.min(limit, (int) sourceCount), 0);
            List<Map<String, Object>> targetSample = databaseService.getTableData(
                    targetConfig, targetSchema, tableName, Math.min(limit, (int) targetCount), 0);

            result.put("sourceSample", sourceSample);
            result.put("targetSample", targetSample);

            result.put("success", true);
        } catch (SQLException e) {
            logger.error("数据详情对比失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ==================== 导出报告 (#17) ====================

    @GetMapping("/export-report")
    public void exportReport(HttpServletResponse response) {
        if (currentDiffResult == null) {
            try {
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("请先执行数据库比对");
            } catch (IOException e) {
                logger.error("导出报告失败", e);
            }
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "DM_Diff_Report_" + timestamp + ".html";
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        try (PrintWriter writer = response.getWriter()) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang='zh-CN'>");
            writer.println("<head><meta charset='UTF-8'><title>达梦数据库对比报告</title>");
            writer.println("<style>");
            writer.println("body { font-family: 'Microsoft YaHei', sans-serif; margin: 20px; color: #333; }");
            writer.println("h1 { color: #0d6efd; border-bottom: 2px solid #0d6efd; padding-bottom: 10px; }");
            writer.println("h2 { color: #495057; margin-top: 24px; }");
            writer.println(".summary { display: flex; gap: 16px; margin: 16px 0; }");
            writer.println(".summary-item { padding: 12px 20px; border-radius: 8px; color: white; font-weight: bold; }");
            writer.println(".bg-add { background: #198754; } .bg-del { background: #dc3545; } .bg-mod { background: #ffc107; color: #000; }");
            writer.println("table { border-collapse: collapse; width: 100%; margin: 12px 0; }");
            writer.println("th, td { border: 1px solid #dee2e6; padding: 8px 12px; text-align: left; }");
            writer.println("th { background: #f8f9fa; font-weight: 600; }");
            writer.println(".tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; color: white; }");
            writer.println(".tag-add { background: #198754; } .tag-del { background: #dc3545; } .tag-mod { background: #ffc107; color: #000; }");
            writer.println(".footer { margin-top: 32px; padding-top: 12px; border-top: 1px solid #dee2e6; color: #6c757d; font-size: 12px; }");
            writer.println("pre { background: #f5f5f5; padding: 8px; border-radius: 4px; overflow-x: auto; }");
            writer.println("</style></head><body>");

            writer.println("<h1>达梦数据库对比报告</h1>");
            writer.println("<p>生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</p>");
            writer.println("<p>源库: " + sourceConfig.getHost() + ":" + sourceConfig.getPort() + "/" + sourceConfig.getDatabase() + "</p>");
            writer.println("<p>目标库: " + targetConfig.getHost() + ":" + targetConfig.getPort() + "/" + targetConfig.getDatabase() + "</p>");

            writer.println("<div class='summary'>");
            writer.println("<div class='summary-item bg-add'>新增表: " + currentDiffResult.getAddedTables().size() + "</div>");
            writer.println("<div class='summary-item bg-del'>删除表: " + currentDiffResult.getDeletedTables().size() + "</div>");
            writer.println("<div class='summary-item bg-mod'>修改表: " + currentDiffResult.getModifiedTables().size() + "</div>");
            writer.println("</div>");

            if (!currentDiffResult.getAddedTables().isEmpty()) {
                writer.println("<h2>新增表</h2>");
                for (com.example.dmdiff.diff.TableDiff td : currentDiffResult.getAddedTables()) {
                    writer.println("<h3>" + td.getTableName() + "</h3>");
                    writer.println("<table><thead><tr><th>字段名</th><th>类型</th><th>长度</th><th>可空</th><th>默认值</th></tr></thead><tbody>");
                    for (com.example.dmdiff.diff.ColumnDiff cd : td.getColumnDiffs()) {
                        com.example.dmdiff.metadata.ColumnInfo col = cd.getTargetColumn();
                        if (col == null) col = cd.getSourceColumn();
                        if (col != null) {
                            writer.println("<tr><td>" + col.getColumnName() + "</td><td>" + col.getDataType()
                                    + "</td><td>" + col.getDataLength() + "</td><td>" + (col.isNullable() ? "是" : "否")
                                    + "</td><td>" + (col.getDefaultValue() != null ? col.getDefaultValue() : "") + "</td></tr>");
                        }
                    }
                    writer.println("</tbody></table>");
                }
            }

            if (!currentDiffResult.getDeletedTables().isEmpty()) {
                writer.println("<h2>删除表</h2>");
                for (com.example.dmdiff.diff.TableDiff td : currentDiffResult.getDeletedTables()) {
                    writer.println("<p><strong>" + td.getTableName() + "</strong> - 表在目标库中不存在</p>");
                }
            }

            if (!currentDiffResult.getModifiedTables().isEmpty()) {
                writer.println("<h2>修改表</h2>");
                for (com.example.dmdiff.diff.TableDiff td : currentDiffResult.getModifiedTables()) {
                    writer.println("<h3>" + td.getTableName() + "</h3>");
                    if (!td.getColumnDiffs().isEmpty()) {
                        writer.println("<h4>字段变更</h4>");
                        writer.println("<table><thead><tr><th>操作</th><th>字段名</th><th>变更详情</th></tr></thead><tbody>");
                        for (com.example.dmdiff.diff.ColumnDiff cd : td.getColumnDiffs()) {
                            String tagClass = cd.getDiffType() == com.example.dmdiff.diff.DiffType.ADD ? "tag-add"
                                    : cd.getDiffType() == com.example.dmdiff.diff.DiffType.DELETE ? "tag-del" : "tag-mod";
                            writer.println("<tr><td><span class='tag " + tagClass + "'>" + cd.getDiffType() + "</span></td>"
                                    + "<td>" + cd.getColumnName() + "</td>"
                                    + "<td>" + (cd.getChangeDetail() != null ? cd.getChangeDetail() : "") + "</td></tr>");
                        }
                        writer.println("</tbody></table>");
                    }
                    if (!td.getIndexDiffs().isEmpty()) {
                        writer.println("<h4>索引变更</h4>");
                        writer.println("<table><thead><tr><th>操作</th><th>索引名</th></tr></thead><tbody>");
                        for (com.example.dmdiff.diff.IndexDiff id : td.getIndexDiffs()) {
                            String tagClass = id.getDiffType() == com.example.dmdiff.diff.DiffType.ADD ? "tag-add"
                                    : id.getDiffType() == com.example.dmdiff.diff.DiffType.DELETE ? "tag-del" : "tag-mod";
                            writer.println("<tr><td><span class='tag " + tagClass + "'>" + id.getDiffType() + "</span></td>"
                                    + "<td>" + id.getIndexName() + "</td></tr>");
                        }
                        writer.println("</tbody></table>");
                    }
                }
            }

            writer.println("<div class='footer'>");
            writer.println("<p>DM-Diff 数据库对比工具 - 自动生成报告</p>");
            writer.println("</div>");
            writer.println("</body></html>");
        } catch (IOException e) {
            logger.error("导出报告失败", e);
        }
    }

    @GetMapping("/export-data-compare")
    public void exportDataCompare(HttpServletResponse response) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "DM_DataCompare_" + timestamp + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        try (PrintWriter writer = response.getWriter()) {
            writer.println("表名,源库行数,目标库行数,差异行数,是否匹配");
            try {
                String sourceSchema = sourceConfig.getDatabase();
                String targetSchema = targetConfig.getDatabase();
                List<String> sourceTables = databaseService.getAllTableNames(sourceConfig);
                List<String> targetTables = databaseService.getAllTableNames(targetConfig);

                for (String tableName : sourceTables) {
                    long sourceCount = databaseService.getTableRowCount(sourceConfig, sourceSchema, tableName);
                    long targetCount = 0;
                    if (targetTables.contains(tableName)) {
                        targetCount = databaseService.getTableRowCount(targetConfig, targetSchema, tableName);
                    }
                    writer.println(tableName + "," + sourceCount + "," + targetCount
                            + "," + Math.abs(sourceCount - targetCount) + ","
                            + (sourceCount == targetCount ? "是" : "否"));
                }
            } catch (SQLException e) {
                writer.println("错误: " + e.getMessage());
            }
        } catch (IOException e) {
            logger.error("导出数据对比CSV失败", e);
        }
    }
}