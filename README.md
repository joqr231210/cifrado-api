# API de Cifrado de Archivos TXT

Una aplicación web REST API desarrollada para cifrar archivos de texto utilizando algoritmo 3DES, desplegada en Railway con sistema de tokens temporales para descarga segura.

## Arquitectura de la Aplicación

### Componentes Principales

```
┌─────────────────┐    ┌──────────────────┐    ┌───────────────────┐
│     Cliente     │───▶│   Spring Boot    │───▶│  JAR de Cifrado   │
│   (Frontend/    │    │     REST API     │    │     (3DES)        │
│    cURL/App)    │    │                  │    │                   │
└─────────────────┘    └──────────────────┘    └───────────────────┘
                               │
                               ▼
                       ┌──────────────────┐
                       │ Sistema de Tokens │
                       │ (Almacenamiento   │
                       │  Temporal 24h)    │
                       └──────────────────┘
```

### Stack Tecnológico
- **Framework**: Spring Boot 2.6.15
- **Runtime**: Java 11
- **Despliegue**: Railway (Contenedor Docker)
- **Cifrado**: Librería externa JAR con algoritmo 3DES
- **Almacenamiento**: Sistema de archivos temporal con limpieza automática

### Flujo de Procesamiento
1. **Upload**: Cliente sube archivo .txt via multipart/form-data
2. **Cifrado**: Integración con JAR externo que implementa algoritmo 3DES
3. **Almacenamiento**: Archivo cifrado se almacena temporalmente con token único
4. **Respuesta**: API devuelve URL de descarga con expiración de 24 horas
5. **Limpieza**: Sistema elimina archivos expirados automáticamente

## Propósito y Funcionalidad

Esta aplicación tiene como objetivo principal **cifrar archivos de texto (.txt) utilizando el algoritmo criptográfico 3DES (Triple Data Encryption Standard)**, proporcionando:

- **Seguridad**: Cifrado robusto mediante 3DES
- **Accesibilidad**: Interface REST API simple
- **Temporalidad**: Links de descarga con expiración automática
- **Escalabilidad**: Arquitectura cloud-native en Railway

## API Endpoints

### Base URL
```
https://cifrado-api-production.up.railway.app
```

### Endpoints Disponibles

#### 1. Health Check
```http
GET /api/cifrado/health
```
**Descripción**: Verificar estado de la API

**Respuesta**:
```json
{
  "status": "OK",
  "service": "Cifrado API"
}
```

#### 2. Cifrar Archivo
```http
POST /api/cifrado/cifrar
Content-Type: multipart/form-data
```
**Descripción**: Sube y cifra un archivo de texto

**Parámetros**:
- `archivo` (file): Archivo .txt a cifrar

**Ejemplo**:
```bash
curl -X POST -F "archivo=@documento.txt" \
  https://cifrado-api-production.up.railway.app/api/cifrado/cifrar
```

**Respuesta exitosa**:
```json
{
  "success": true,
  "message": "Archivo cifrado exitosamente",
  "downloadUrl": "https://cifrado-api-production.up.railway.app/api/cifrado/download/abc123...",
  "token": "593907e9-6696-4345-9fd0-52ba2fb18ac9",
  "originalFileName": "documento.txt",
  "encryptedFileName": "documento.cif",
  "expiresAt": "2025-09-04T15:15:03",
  "validFor": "24 horas"
}
```

#### 3. Descargar Archivo Cifrado
```http
GET /api/cifrado/download/{token}
```
**Descripción**: Descarga el archivo cifrado usando el token

**Parámetros**:
- `token` (path): Token único recibido al cifrar

**Ejemplo**:
```bash
curl https://cifrado-api-production.up.railway.app/api/cifrado/download/593907e9-6696-4345-9fd0-52ba2fb18ac9 \
  -o archivo_cifrado.cif
```

**Respuesta**: Archivo binario (.cif) con contenido cifrado

#### 4. Información de Token
```http
GET /api/cifrado/info/{token}
```
**Descripción**: Obtener información sobre un token específico

**Parámetros**:
- `token` (path): Token a consultar

**Respuesta**:
```json
{
  "token": "593907e9-6696-4345-9fd0-52ba2fb18ac9",
  "originalFileName": "documento.cif",
  "createdAt": "2025-09-03T15:15:03",
  "expiresAt": "2025-09-04T15:15:03",
  "expired": false
}
```

## Uso de la API

### Cifrar un archivo
```bash
# 1. Subir archivo y obtener URL de descarga
curl -X POST -F "archivo=@mi_documento.txt" \
  https://cifrado-api-production.up.railway.app/api/cifrado/cifrar

# 2. Usar el token recibido para descargar archivo cifrado
curl https://cifrado-api-production.up.railway.app/api/cifrado/download/TU_TOKEN \
  -o documento_cifrado.cif
```

### Restricciones
- Solo acepta archivos con extensión `.txt`
- Tamaño máximo de archivo: 10MB
- Los tokens expiran en 24 horas
- Un archivo por petición

### Códigos de Error Comunes
- `400 Bad Request`: Archivo vacío o extensión no válida
- `404 Not Found`: Token no válido o expirado  
- `500 Internal Server Error`: Error en el proceso de cifrado

## Desarrollo Local

### Prerequisitos
- Java 11
- Maven 3.6+
- Archivo JAR de cifrado en `libs/`
- Archivos keystore en `src/main/resources/keystore/`

### Ejecutar localmente
```bash
mvn clean compile
mvn spring-boot:run
```

La aplicación estará disponible en `http://localhost:8080`

## Deployment

La aplicación se despliega automáticamente en Railway mediante integración con GitHub. Cada push al branch `main` activa un nuevo deployment.

### Variables de Entorno (Railway)
- `PORT=8080`
- `JAVA_TOOL_OPTIONS=-Xmx512m`