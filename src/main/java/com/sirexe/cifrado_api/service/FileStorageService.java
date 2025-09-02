package com.sirexe.cifradoapi.service;

import com.sirexe.cifradoapi.model.FileToken;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileStorageService {
    
    // Almacenamiento en memoria para tokens (en producción usar base de datos)
    private final ConcurrentHashMap<String, FileToken> tokenStorage = new ConcurrentHashMap<>();
    
    // Directorio para archivos cifrados
    private final String uploadDir = System.getProperty("java.io.tmpdir") + "/cifrado_uploads/";
    
    public FileStorageService() {
        // Crear directorio si no existe
        createUploadDirectory();
        // Iniciar limpieza automática cada hora
        startCleanupTask();
    }
    
    private void createUploadDirectory() {
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("Directorio de uploads creado: " + uploadDir);
            }
        } catch (IOException e) {
            System.err.println("Error creando directorio de uploads: " + e.getMessage());
        }
    }
    
    public FileToken storeFile(byte[] fileContent, String originalFileName) throws IOException {
        // Generar token único
        String token = UUID.randomUUID().toString();
        
        // Generar nombre único para el archivo
        String fileExtension = getFileExtension(originalFileName);
        String storedFileName = token + fileExtension;
        String filePath = uploadDir + storedFileName;
        
        // Guardar archivo
        Files.write(Paths.get(filePath), fileContent);
        
        // Crear registro de token
        FileToken fileToken = new FileToken(token, storedFileName, originalFileName, filePath);
        tokenStorage.put(token, fileToken);
        
        System.out.println("Archivo almacenado: " + originalFileName + " -> " + storedFileName);
        return fileToken;
    }
    
    public byte[] getFile(String token) throws IOException {
        FileToken fileToken = tokenStorage.get(token);
        
        if (fileToken == null) {
            throw new RuntimeException("Token no válido");
        }
        
        if (fileToken.isExpired()) {
            // Limpiar archivo expirado
            deleteFile(token);
            throw new RuntimeException("El archivo ha expirado");
        }
        
        // Leer y devolver archivo
        Path filePath = Paths.get(fileToken.getFilePath());
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Archivo no encontrado");
        }
        
        return Files.readAllBytes(filePath);
    }
    
    public FileToken getFileToken(String token) {
        FileToken fileToken = tokenStorage.get(token);
        if (fileToken != null && fileToken.isExpired()) {
            deleteFile(token);
            return null;
        }
        return fileToken;
    }
    
    public void deleteFile(String token) {
        FileToken fileToken = tokenStorage.remove(token);
        if (fileToken != null) {
            try {
                Files.deleteIfExists(Paths.get(fileToken.getFilePath()));
                System.out.println("Archivo eliminado: " + fileToken.getFileName());
            } catch (IOException e) {
                System.err.println("Error eliminando archivo: " + e.getMessage());
            }
        }
    }
    
    private String getFileExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return "";
    }
    
    private void startCleanupTask() {
        // Ejecutar limpieza cada hora
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3600000); // 1 hora
                    cleanupExpiredFiles();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    private void cleanupExpiredFiles() {
        LocalDateTime now = LocalDateTime.now();
        tokenStorage.entrySet().removeIf(entry -> {
            FileToken token = entry.getValue();
            if (token.isExpired()) {
                try {
                    Files.deleteIfExists(Paths.get(token.getFilePath()));
                    System.out.println("Archivo expirado eliminado: " + token.getFileName());
                } catch (IOException e) {
                    System.err.println("Error eliminando archivo expirado: " + e.getMessage());
                }
                return true;
            }
            return false;
        });
    }
}