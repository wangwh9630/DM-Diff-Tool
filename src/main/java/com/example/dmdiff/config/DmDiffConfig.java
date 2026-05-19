package com.example.dmdiff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dm-diff")
public class DmDiffConfig {
    
    private DatabaseConfig source = new DatabaseConfig();
    private DatabaseConfig target = new DatabaseConfig();
    
    public DatabaseConfig getSource() {
        return source;
    }
    
    public void setSource(DatabaseConfig source) {
        this.source = source;
    }
    
    public DatabaseConfig getTarget() {
        return target;
    }
    
    public void setTarget(DatabaseConfig target) {
        this.target = target;
    }
    
    public static class DatabaseConfig {
        private String host = "localhost";
        private int port = 5236;
        private String database = "SYSDBA";
        private String username = "SYSDBA";
        private String password = "Aa123456";
        
        public String getHost() {
            return host;
        }
        
        public void setHost(String host) {
            this.host = host;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
        
        public String getDatabase() {
            return database;
        }
        
        public void setDatabase(String database) {
            this.database = database;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }
}
