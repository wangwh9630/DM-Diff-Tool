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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private ConnectionConfig sourceConfig = new ConnectionConfig();
    private ConnectionConfig targetConfig = new ConnectionConfig();
    private DiffResult currentDiffResult;

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
                       @RequestParam(required = false) List<String> blacklist) {
        try {
            DiffConfig diffConfig = new DiffConfig();
            diffConfig.setSourceConfig(sourceConfig);
            diffConfig.setTargetConfig(targetConfig);
            diffConfig.setIgnoreCase(ignoreCase);
            diffConfig.setBlacklist(blacklist);
            
            currentDiffResult = diffService.compare(diffConfig);
            
            model.addAttribute("diffResult", currentDiffResult);
            model.addAttribute("ignoreCase", ignoreCase);
            model.addAttribute("blacklist", blacklist);
            
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
        
        String upgradeSql = sqlGeneratorService.generateUpgradeSql(currentDiffResult);
        String rollbackSql = sqlGeneratorService.generateRollbackSql(currentDiffResult);
        
        model.addAttribute("upgradeSql", upgradeSql);
        model.addAttribute("rollbackSql", rollbackSql);
        
        return "sql";
    }

    private List<SqlStatement> executeStatements(List<SqlStatement> statements) {
        for (SqlStatement stmt : statements) {
            try {
                databaseService.executeSql(sourceConfig, stmt.getSql());
                stmt.setSuccess(true);
            } catch (SQLException e) {
                stmt.setSuccess(false);
                stmt.setErrorMessage(e.getMessage());
                logger.error("SQL执行失败 [{}] {}: {}", stmt.getActionType(), stmt.getTableName(), e.getMessage());
            }
        }
        return statements;
    }

    @PostMapping("/execute-create")
    @ResponseBody
    public List<SqlStatement> executeCreate() {
        if (currentDiffResult == null) return List.of();
        return executeStatements(sqlGeneratorService.generateCreate(currentDiffResult));
    }

    @PostMapping("/execute-alter")
    @ResponseBody
    public List<SqlStatement> executeAlter() {
        if (currentDiffResult == null) return List.of();
        return executeStatements(sqlGeneratorService.generateAlter(currentDiffResult));
    }

    @PostMapping("/execute-drop")
    @ResponseBody
    public List<SqlStatement> executeDrop() {
        if (currentDiffResult == null) return List.of();
        return executeStatements(sqlGeneratorService.generateDrop(currentDiffResult));
    }

    @PostMapping("/execute-all")
    @ResponseBody
    public Map<String, Object> executeAll(@RequestParam String sqlType) {
        Map<String, Object> result = new HashMap<>();
        if (currentDiffResult == null) {
            result.put("success", false);
            result.put("message", "请先执行数据库比对");
            return result;
        }

        List<SqlStatement> allStatements;
        if ("upgrade".equalsIgnoreCase(sqlType)) {
            allStatements = sqlGeneratorService.generateCreate(currentDiffResult);
            allStatements.addAll(sqlGeneratorService.generateAlter(currentDiffResult));
            allStatements.addAll(sqlGeneratorService.generateDrop(currentDiffResult));
        } else if ("rollback".equalsIgnoreCase(sqlType)) {
            allStatements = sqlGeneratorService.rollbackAll(currentDiffResult);
        } else {
            result.put("success", false);
            result.put("message", "未知的 SQL 类型");
            return result;
        }

        executeStatements(allStatements);

        int successCount = 0;
        int failCount = 0;
        for (SqlStatement stmt : allStatements) {
            if (stmt.isSuccess()) successCount++;
            else failCount++;
        }

        result.put("success", failCount == 0);
        result.put("message", "成功 " + successCount + " 条，失败 " + failCount + " 条");
        result.put("statements", allStatements);
        return result;
    }
}