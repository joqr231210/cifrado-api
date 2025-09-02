package com.sirexe.cifradoapi.model;

import java.time.LocalDateTime;

public class FileToken {
    private String token;
    private String fileName;
    private String originalName;
    private String filePath;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    
    public FileToken() {}
    
    public FileToken(String token, String fileName, String originalName, String filePath) {
        this.token = token;
        this.fileName = fileName;
        this.originalName = originalName;
        this.filePath = filePath;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusHours(24); // Expira en 24 horas
    }
    
    // Getters y setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}