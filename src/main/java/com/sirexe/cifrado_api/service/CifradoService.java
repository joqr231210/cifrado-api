package com.sirexe.cifradoapi.service;

import cifrado.Cifrar;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        
        try {
            // Guardar archivo original temporalmente
            File archivoTemporal = new File(tempDir + nombreOriginal);
            archivo.transferTo(archivoTemporal);
            
            // Obtener directorio de claves desde resources
            String directorioClaves = obtenerDirectorioClaves();
            
            // Cambiar al directorio temporal para que el JAR genere el archivo ahí
            String directorioOriginal = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir);
            
            // Logging para debug (igual que tu programa original)
            System.out.println("extension: " + extension);
            System.out.println("nombre de archivo: " + nombreSinExtension);
            System.out.println("directorio de claves:" + directorioClaves);
            
            try {
                // Llamar al método de cifrado EXACTAMENTE como en tu programa
                Cifrar.cifra(extension, nombreSinExtension, directorioClaves);
                System.out.println("Cifrado completado exitosamente.");
                
            } catch (Exception e) {
                System.err.println("Error durante el cifrado: " + e.getMessage());
                e.printStackTrace();
                throw e;
            } finally {
                // Restaurar directorio original
                System.setProperty("user.dir", directorioOriginal);
            }
            
            // Buscar el archivo cifrado DESPUÉS del cifrado exitoso
            File archivoCifrado = buscarArchivoCifrado(tempDir, nombreSinExtension, extension);
            
            // Si no lo encuentra en temp, buscar en directorio original
            if (archivoCifrado == null) {
                String directorioOriginalRestaurado = System.getProperty("user.dir");
                File dirOriginal = new File(directorioOriginalRestaurado);
                File[] archivosEnOriginal = dirOriginal.listFiles((d, name) -> 
                    name.contains(nombreSinExtension) && !name.equals(nombreSinExtension + extension));
                
                if (archivosEnOriginal != null && archivosEnOriginal.length > 0) {
                    archivoCifrado = archivosEnOriginal[0];
                    System.out.println("Usando archivo del directorio original: " + archivoCifrado.getName());
                }
            }
            
            if (archivoCifrado == null || !archivoCifrado.exists()) {
                throw new RuntimeException("No se pudo generar el archivo cifrado");
            }
            
            // Leer el archivo cifrado
            byte[] contenidoCifrado = Files.readAllBytes(archivoCifrado.toPath());
            System.out.println("Archivo cifrado leído: " + archivoCifrado.getName() + " (" + contenidoCifrado.length + " bytes)");
            
            return contenidoCifrado;
            
        } finally {
            // Limpiar directorio temporal
            limpiarDirectorioTemporal(directorioTemporal);
        }
    }
    
    private String obtenerDirectorioClaves() throws Exception {
        try {
            // En contenedor, necesitamos copiar los archivos del JAR a un directorio temporal
            String tempKeysDir = System.getProperty("java.io.tmpdir") + "/keystore_temp/";
            File tempDir = new File(tempKeysDir);
            tempDir.mkdirs();
            
            // Listar recursos en keystore
            var resource = getClass().getClassLoader().getResource("keystore/");
            if (resource != null) {
                // Si es un directorio normal (desarrollo local)
                if (resource.getProtocol().equals("file")) {
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
                    // Si es dentro de un JAR (producción), copiar archivos
                    System.out.println("Copiando archivos de keystore desde JAR...");
                    
                    // Lista de archivos de keystore que realmente tienes
                    String[] archivosKeystore = {
                        "transferencia.jks"
                        // Añade aquí otros archivos si los tienes
                    };
                    
                    for (String nombreArchivo : archivosKeystore) {
                        try {
                            var archivoResource = getClass().getClassLoader().getResourceAsStream("keystore/" + nombreArchivo);
                            if (archivoResource != null) {
                                Files.copy(archivoResource, Paths.get(tempKeysDir + nombreArchivo));
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
            nombreBase + extensionOriginal + ".enc",
            nombreBase + ".enc", 
            nombreBase + "_cifrado" + extensionOriginal,
            nombreBase + extensionOriginal + "_cifrado",
            nombreBase + ".cifrado"
        };
        
        for (String nombre : posiblesNombres) {
            File archivo = new File(directorio + nombre);
            if (archivo.exists()) {
                System.out.println("Archivo cifrado encontrado: " + archivo.getName());
                return archivo;
            }
        }
        
        // Si no encuentra con nombres conocidos, buscar cualquier archivo nuevo que no sea el original
        File dir = new File(directorio);
        File[] archivos = dir.listFiles((d, name) -> 
            name.contains(nombreBase) && !name.equals(nombreBase + extensionOriginal));
        
        if (archivos != null && archivos.length > 0) {
            System.out.println("Archivo cifrado encontrado: " + archivos[0].getName());
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