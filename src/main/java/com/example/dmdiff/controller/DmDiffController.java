package com.example.dmdiff.controller;

import com.example.dmdiff.config.DmDiffConfig;
import com.example.dmdiff.diff.DiffResult;
import com.example.dmdiff.dto.ConnectionConfig;
import com.example.dmdiff.dto.ConnectionResult;
import com.example.dmdiff.dto.DiffConfig;
import com.example.dmdiff.dto.SqlStatement;
import com.example.dmdiff.service.DatabaseService;
import com.example.dmdiff.service.DiffService;
import com.example.dmdiff.service.SqlGeneratorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
}