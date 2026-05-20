package com.example.dmdiff.service;

import com.example.dmdiff.dto.ConnectionConfig;
import com.example.dmdiff.dto.MigrationProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);

    @Autowired
    private DatabaseService databaseService;

    public void migrateTables(ConnectionConfig sourceConfig, ConnectionConfig targetConfig,
                               List<String> tableNames, boolean migrateStructure,
                               boolean migrateData, int batchSize,
                               Consumer<MigrationProgress> progressCallback) throws Exception {
        String sourceSchema = sourceConfig.getDatabase();
        String targetSchema = targetConfig.getDatabase();

        int totalSteps = 0;
        if (migrateStructure) totalSteps += tableNames.size();
        if (migrateData) totalSteps += tableNames.size();

        AtomicInteger completed = new AtomicInteger(0);

        for (String tableName : tableNames) {
            if (migrateStructure) {
                MigrationProgress structProgress = new MigrationProgress();
                structProgress.setTotal(totalSteps);
                structProgress.setCompleted(completed.get());
                structProgress.setTableName(tableName);
                structProgress.setPhase("STRUCTURE");
                structProgress.setDone(false);

                try {
                    String ddl = databaseService.getTableDdl(sourceConfig, sourceSchema, tableName);
                    if (ddl != null && !ddl.trim().isEmpty()) {
                        String targetDdl = ddl.replace("\"" + sourceSchema + "\"", "\"" + targetSchema + "\"")
                                .replace(sourceSchema + ".", targetSchema + ".");
                        try {
                            databaseService.executeSqlOnTarget(targetConfig, targetDdl);
                            structProgress.setSuccess(true);
                            structProgress.setMessage("表结构迁移成功");
                        } catch (SQLException e) {
                            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                                structProgress.setSuccess(true);
                                structProgress.setMessage("表已存在，跳过创建");
                            } else {
                                structProgress.setSuccess(false);
                                structProgress.setMessage("结构迁移失败: " + e.getMessage());
                            }
                        }
                    } else {
                        structProgress.setSuccess(false);
                        structProgress.setMessage("无法获取表DDL");
                    }
                } catch (Exception e) {
                    structProgress.setSuccess(false);
                    structProgress.setMessage("结构迁移异常: " + e.getMessage());
                }

                structProgress.setCompleted(completed.incrementAndGet());
                if (progressCallback != null) {
                    progressCallback.accept(structProgress);
                }
            }

            if (migrateData) {
                MigrationProgress dataProgress = new MigrationProgress();
                dataProgress.setTotal(totalSteps);
                dataProgress.setCompleted(completed.get());
                dataProgress.setTableName(tableName);
                dataProgress.setPhase("DATA");
                dataProgress.setDone(false);

                try {
                    long sourceCount = databaseService.getTableRowCount(sourceConfig, sourceSchema, tableName);
                    dataProgress.setMessage("源表行数: " + sourceCount);

                    if (sourceCount > 0) {
                        databaseService.disableConstraints(targetConfig, targetSchema, tableName);
                        databaseService.truncateTable(targetConfig, targetSchema, tableName);

                        List<String> columns = databaseService.getTableColumns(sourceConfig, sourceSchema, tableName);
                        long totalMigrated = 0;
                        int offset = 0;

                        while (offset < sourceCount) {
                            List<Map<String, Object>> rows = databaseService.getTableData(
                                    sourceConfig, sourceSchema, tableName, batchSize, offset);
                            if (rows.isEmpty()) break;

                            int inserted = databaseService.batchInsertData(
                                    targetConfig, targetSchema, tableName, columns, rows);
                            totalMigrated += inserted;
                            offset += batchSize;
                        }

                        databaseService.enableConstraints(targetConfig, targetSchema, tableName);

                        long targetCount = databaseService.getTableRowCount(targetConfig, targetSchema, tableName);
                        dataProgress.setSuccess(totalMigrated == sourceCount && targetCount == sourceCount);
                        dataProgress.setMessage("数据迁移完成: 迁移 " + totalMigrated
                                + " 行, 目标表行数: " + targetCount);
                    } else {
                        dataProgress.setSuccess(true);
                        dataProgress.setMessage("源表无数据，跳过迁移");
                    }
                } catch (Exception e) {
                    dataProgress.setSuccess(false);
                    dataProgress.setMessage("数据迁移失败: " + e.getMessage());
                    logger.error("数据迁移失败: {} - {}", tableName, e.getMessage());
                }

                dataProgress.setCompleted(completed.incrementAndGet());
                if (progressCallback != null) {
                    progressCallback.accept(dataProgress);
                }
            }
        }

        MigrationProgress doneProgress = new MigrationProgress();
        doneProgress.setTotal(totalSteps);
        doneProgress.setCompleted(totalSteps);
        doneProgress.setDone(true);
        doneProgress.setMessage("迁移完成");
        if (progressCallback != null) {
            progressCallback.accept(doneProgress);
        }
    }
}