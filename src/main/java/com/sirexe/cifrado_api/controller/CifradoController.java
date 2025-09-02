package com.sirexe.cifradoapi.controller;

import com.sirexe.cifradoapi.service.CifradoService;
import com.sirexe.cifradoapi.service.FileStorageService;
import com.sirexe.cifradoapi.model.FileToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cifrado")
@CrossOrigin(origins = "*") // Para permitir llamadas desde frontend
public class CifradoController {
    
    @Autowired
    private CifradoService cifradoService;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("service", "Cifrado API");
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/cifrar")
    public ResponseEntity<?> cifrarArchivo(@RequestParam("archivo") MultipartFile archivo, 
                                          HttpServletRequest request) {
        try {
            // Validaciones básicas
            if (archivo.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(crearRespuestaError("El archivo está vacío"));
            }
            
            String nombreOriginal = archivo.getOriginalFilename();
            if (nombreOriginal == null || !nombreOriginal.endsWith(".txt")) {
                return ResponseEntity.badRequest()
                    .body(crearRespuestaError("Solo se permiten archivos .txt"));
            }
            
            // Procesar cifrado
            byte[] archivoCifrado = cifradoService.cifrarArchivo(archivo);
            
            // Almacenar archivo cifrado y obtener token
            FileToken fileToken = fileStorageService.storeFile(archivoCifrado, nombreOriginal.replace(".txt", ".cif"));
            
            // Construir URL de descarga
            String baseUrl = getBaseUrl(request);
            String downloadUrl = baseUrl + "/api/cifrado/download/" + fileToken.getToken();
            
            // Crear respuesta con información del archivo
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Archivo cifrado exitosamente");
            response.put("downloadUrl", downloadUrl);
            response.put("token", fileToken.getToken());
            response.put("originalFileName", nombreOriginal);
            response.put("encryptedFileName", fileToken.getOriginalName());
            response.put("expiresAt", fileToken.getExpiresAt().toString());
            response.put("validFor", "24 horas");
            
            return ResponseEntity.ok(response);
                
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(crearRespuestaError("Error durante el cifrado: " + e.getMessage()));
        }
    }
    
    @GetMapping("/download/{token}")
    public ResponseEntity<?> downloadFile(@PathVariable String token) {
        try {
            // Obtener información del archivo
            FileToken fileToken = fileStorageService.getFileToken(token);
            
            if (fileToken == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(crearRespuestaError("Token no válido o archivo expirado"));
            }
            
            // Obtener contenido del archivo
            byte[] fileContent = fileStorageService.getFile(token);
            
            // Configurar headers para descarga
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + fileToken.getOriginalName() + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent);
                
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(crearRespuestaError("Error descargando archivo: " + e.getMessage()));
        }
    }
    
    @GetMapping("/info/{token}")
    public ResponseEntity<?> getFileInfo(@PathVariable String token) {
        try {
            FileToken fileToken = fileStorageService.getFileToken(token);
            
            if (fileToken == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(crearRespuestaError("Token no válido o archivo expirado"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", fileToken.getToken());
            response.put("originalFileName", fileToken.getOriginalName());
            response.put("createdAt", fileToken.getCreatedAt().toString());
            response.put("expiresAt", fileToken.getExpiresAt().toString());
            response.put("expired", fileToken.isExpired());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(crearRespuestaError("Error obteniendo información: " + e.getMessage()));
        }
    }
    
    private String getBaseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() + 
               (request.getServerPort() != 80 && request.getServerPort() != 443 
                ? ":" + request.getServerPort() : "");
    }
    
    private Map<String, String> crearRespuestaError(String mensaje) {
        Map<String, String> error = new HashMap<>();
        error.put("error", mensaje);
        error.put("timestamp", java.time.Instant.now().toString());
        return error;
    }
}