package com.sirexe.cifradoapi.service;

import cifrado.Cifrar;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class CifradoService {
    
    public byte[] cifrarArchivo(MultipartFile archivo) throws Exception {
        String nombreOriginal = archivo.getOriginalFilename();
        String nombreSinExtension = nombreOriginal.substring(0, nombreOriginal.lastIndexOf('.'));
        String extension = nombreOriginal.substring(nombreOriginal.lastIndexOf('.'));
        
        // Crear directorio temporal único para este proceso
        String timestamp = String.valueOf(System.currentTimeMillis());
        String tempDir = System.getProperty("java.io.tmpdir") + "/cifrado_" + timestamp + "/";
        File directorioTemporal = new File(tempDir);
        directorioTemporal.mkdirs();
        
        // Variables para limpieza
        File archivoEnDirectorioActual = null;
        File archivoCifradoGenerado = null;
        
        try {
            // 1. Guardar archivo original temporalmente
            File archivoTemporal = new File(tempDir + nombreOriginal);
            archivo.transferTo(archivoTemporal);
            
            // 2. Copiar archivo al directorio de trabajo actual (/app en Railway)
            String directorioActual = System.getProperty("user.dir");
            archivoEnDirectorioActual = new File(directorioActual, nombreOriginal);
            Files.copy(archivoTemporal.toPath(), archivoEnDirectorioActual.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            // 3. Obtener directorio de claves desde resources
            String directorioClaves = obtenerDirectorioClaves();
            
            // Logging para debug
            System.out.println("extension: " + extension);
            System.out.println("nombre de archivo: " + nombreSinExtension);
            System.out.println("directorio de claves: " + directorioClaves);
            System.out.println("directorio actual: " + directorioActual);
            System.out.println("archivo copiado a: " + archivoEnDirectorioActual.getAbsolutePath());
            System.out.println("archivo copiado existe: " + archivoEnDirectorioActual.exists());
            
            // 4. Llamar al método de cifrado (desde directorio actual, sin cambiar user.dir)
            try {
                Cifrar.cifra(extension, nombreSinExtension, directorioClaves);
                System.out.println("Cifrado completado exitosamente.");
                
            } catch (Exception e) {
                System.err.println("Error durante el cifrado: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            
            // 5. Buscar el archivo cifrado en el directorio actual
            archivoCifradoGenerado = buscarArchivoCifrado(directorioActual + "/", nombreSinExtension, extension);
            
            if (archivoCifradoGenerado == null || !archivoCifradoGenerado.exists()) {
                // Listar archivos para debug
                File dirActual = new File(directorioActual);
                File[] archivos = dirActual.listFiles();
                System.out.println("Archivos en directorio actual después del cifrado:");
                if (archivos != null) {
                    for (File f : archivos) {
                        System.out.println("  - " + f.getName() + " (" + f.length() + " bytes)");
                    }
                }
                throw new RuntimeException("No se pudo encontrar el archivo cifrado");
            }
            
            System.out.println("Archivo cifrado encontrado: " + archivoCifradoGenerado.getName() + " (" + archivoCifradoGenerado.length() + " bytes)");
            
            // 6. Leer el contenido del archivo cifrado
            byte[] contenidoCifrado = Files.readAllBytes(archivoCifradoGenerado.toPath());
            
            return contenidoCifrado;
            
        } finally {
            // Limpieza: eliminar archivos temporales y generados
            try {
                if (archivoEnDirectorioActual != null && archivoEnDirectorioActual.exists()) {
                    archivoEnDirectorioActual.delete();
                    System.out.println("Archivo original limpiado: " + archivoEnDirectorioActual.getName());
                }
                if (archivoCifradoGenerado != null && archivoCifradoGenerado.exists()) {
                    archivoCifradoGenerado.delete();
                    System.out.println("Archivo cifrado limpiado: " + archivoCifradoGenerado.getName());
                }
                limpiarDirectorioTemporal(directorioTemporal);
            } catch (Exception e) {
                System.err.println("Error en limpieza: " + e.getMessage());
            }
        }
    }
    
    private String obtenerDirectorioClaves() throws Exception {
        try {
            // En contenedor, copiar archivos del JAR a directorio temporal
            String tempKeysDir = System.getProperty("java.io.tmpdir") + "/keystore_temp/";
            File tempDir = new File(tempKeysDir);
            tempDir.mkdirs();
            
            var resource = getClass().getClassLoader().getResource("keystore/");
            if (resource != null) {
                if (resource.getProtocol().equals("file")) {
                    // Desarrollo local - usar directorio directamente
                    String path = resource.getPath();
                    if (path.startsWith("/") && path.contains(":")) {
                        path = path.substring(1);
                    }
                    if (!path.endsWith("/") && !path.endsWith("\\")) {
                        path += "/";
                    }
                    System.out.println("Directorio de claves encontrado (local): " + path);
                    return path;
                } else {
                    // Producción - copiar archivos desde JAR
                    System.out.println("Copiando archivos de keystore desde JAR...");
                    
                    // Solo el archivo que realmente tienes
                    String[] archivosKeystore = {"transferencia.jks"};
                    
                    for (String nombreArchivo : archivosKeystore) {
                        try {
                            var archivoResource = getClass().getClassLoader().getResourceAsStream("keystore/" + nombreArchivo);
                            if (archivoResource != null) {
                                Files.copy(archivoResource, Paths.get(tempKeysDir + nombreArchivo), StandardCopyOption.REPLACE_EXISTING);
                                archivoResource.close();
                                System.out.println("Copiado: " + nombreArchivo);
                            } else {
                                System.out.println("No encontrado: " + nombreArchivo);
                            }
                        } catch (Exception e) {
                            System.out.println("Error copiando " + nombreArchivo + ": " + e.getMessage());
                        }
                    }
                    
                    System.out.println("Directorio de claves temporal: " + tempKeysDir);
                    return tempKeysDir;
                }
            } else {
                throw new RuntimeException("No se encontró el directorio keystore en resources");
            }
        } catch (Exception e) {
            throw new Exception("Error al obtener directorio de claves: " + e.getMessage());
        }
    }
    
    private File buscarArchivoCifrado(String directorio, String nombreBase, String extensionOriginal) {
        // Posibles nombres que podría generar tu JAR
        String[] posiblesNombres = {
            nombreBase + ".cif",
            nombreBase + extensionOriginal + ".cif", 
            nombreBase + ".enc",
            nombreBase + extensionOriginal + ".enc",
            nombreBase + "_cifrado" + extensionOriginal,
            nombreBase + ".cifrado"
        };
        
        for (String nombre : posiblesNombres) {
            File archivo = new File(directorio + nombre);
            if (archivo.exists()) {
                System.out.println("Archivo cifrado encontrado: " + archivo.getName());
                return archivo;
            }
        }
        
        // Si no encuentra con nombres conocidos, buscar cualquier archivo nuevo que contenga el nombre base
        File dir = new File(directorio);
        File[] archivos = dir.listFiles((d, name) -> 
            name.contains(nombreBase) && 
            !name.equals(nombreBase + extensionOriginal) &&
            (name.endsWith(".cif") || name.endsWith(".enc") || name.contains("cifrado")));
        
        if (archivos != null && archivos.length > 0) {
            System.out.println("Archivo cifrado encontrado (búsqueda genérica): " + archivos[0].getName());
            return archivos[0];
        }
        
        return null;
    }
    
    private void limpiarDirectorioTemporal(File directorio) {
        try {
            if (directorio.exists()) {
                File[] archivos = directorio.listFiles();
                if (archivos != null) {
                    for (File archivo : archivos) {
                        archivo.delete();
                    }
                }
                directorio.delete();
            }
        } catch (Exception e) {
            System.err.println("Error limpiando directorio temporal: " + e.getMessage());
        }
    }
}