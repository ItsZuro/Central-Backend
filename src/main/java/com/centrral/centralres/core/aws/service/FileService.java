package com.centrral.centralres.core.aws.service;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.HexFormat;

import javax.imageio.ImageIO;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.centrral.centralres.core.aws.dto.response.DeleteResponse;
import com.centrral.centralres.core.aws.exception.AccessDeniedException;
import com.centrral.centralres.core.aws.exception.FileNotFoundException;
import com.centrral.centralres.core.aws.exception.FileUploadException;
import com.centrral.centralres.core.aws.model.FileDetail;
import com.centrral.centralres.core.aws.model.FileMetadata;
import com.centrral.centralres.core.aws.repository.FileMetadataRepository;
import com.centrral.centralres.core.config.FileProperties;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Service s3Service;
    private final FileMetadataRepository fileRepository;
    private final UserRepository userRepository;
    private final FileProperties fileProperties;

    private long getMaxFileSize() {
        String size = fileProperties.getMaxSize().toUpperCase();
        if (size.endsWith("MB"))
            return Long.parseLong(size.replace("MB", "")) * 1024 * 1024;
        if (size.endsWith("KB"))
            return Long.parseLong(size.replace("KB", "")) * 1024;
        if (size.endsWith("B"))
            return Long.parseLong(size.replace("B", ""));
        return 5 * 1024 * 1024;
    }

    @Transactional
    public FileMetadata uploadFile(MultipartFile file, String folder) {
        validateFolder(folder);
        validateFile(file);
        if ("products".equals(folder)) {
            validateProductImage(file);
            var actor = getCurrentUser();
            boolean authorized = actor.getRoles().stream().anyMatch(role ->
                    "ROLE_ADMIN".equals(role.getName()) || "ROLE_MANAGER".equals(role.getName()));
            if (!authorized) {
                throw new AccessDeniedException("Solo el personal autorizado puede subir imágenes de productos.");
            }
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload-", file.getOriginalFilename());
            file.transferTo(tempFile);

            String key = s3Service.uploadFile(tempFile, folder, file.getContentType());
            String url = s3Service.getFileUrl(key);
            User currentUser = getCurrentUser();

            FileMetadata metadata = FileMetadata.builder()
                    .originalName(file.getOriginalFilename())
                    .key(key)
                    .folder(folder)
                    .url(url)
                    .uploadDate(LocalDateTime.now())
                    .uploadedBy(currentUser)
                    .build();

            Integer width = null;
            Integer height = null;
            BigDecimal duration = null;

            String checksum = calculateChecksum(new FileInputStream(tempFile));

            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.startsWith("image")) {
                    BufferedImage image = ImageIO.read(tempFile);
                    if (image != null) {
                        width = image.getWidth();
                        height = image.getHeight();
                    }
                } else if (contentType.startsWith("video") || contentType.startsWith("audio")) {
                    Double dur = getMediaDuration(tempFile);
                    if (dur != null) {
                        duration = BigDecimal.valueOf(dur).setScale(3, RoundingMode.HALF_UP);
                    }
                }
            }

            FileDetail detail = FileDetail.builder()
                    .contentType(contentType)
                    .size(file.getSize())
                    .width(width)
                    .height(height)
                    .duration(duration)
                    .checksum(checksum)
                    .file(metadata)
                    .build();

            metadata.setDetails(detail);

            FileMetadata saved = fileRepository.save(metadata);
            if ("products".equals(folder)) {
                saved.setUrl(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/files/product-images/")
                        .path(saved.getId().toString())
                        .toUriString());
                saved = fileRepository.save(saved);
            }
            return saved;

        } catch (IOException e) {
            throw new FileUploadException("Error procesando el archivo: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists())
                tempFile.delete();
        }
    }

    public DeleteResponse deleteFile(Long fileId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException("Archivo no encontrado con id: " + fileId));
        User currentUser = getCurrentUser();

        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"));

        if (!isAdmin && !file.getUploadedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("No tiene permisos para eliminar este archivo");
        }

        s3Service.deleteFile(file.getKey());
        fileRepository.delete(file);

        return new DeleteResponse(file.getId(), currentUser.getId(), currentUser.getUsername());
    }

    public record ProductImage(byte[] bytes, String contentType) { }

    @Transactional(readOnly = true)
    public ProductImage readProductImage(Long id) {
        FileMetadata file = fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("Imagen del producto no encontrada."));
        if (!"products".equals(file.getFolder()) || file.getDetails() == null ||
                !isProductImageType(file.getDetails().getContentType())) {
            throw new FileNotFoundException("Imagen del producto no encontrada.");
        }
        return new ProductImage(s3Service.readFile(file.getKey()), file.getDetails().getContentType());
    }

    private static boolean isProductImageType(String type) {
        return "image/jpeg".equals(type) || "image/png".equals(type) || "image/webp".equals(type);
    }

    /** MIME, extensión y firma de archivo; evita subir HTML/SVG disfrazados como imágenes. */
    private void validateProductImage(MultipartFile file) {
        if (file.getSize() > 5L * 1024L * 1024L) {
            throw new FileUploadException("La imagen del producto debe pesar como máximo 5 MB.");
        }
        String fileName = file.getOriginalFilename() == null ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String mime = file.getContentType();
        boolean jpeg = "image/jpeg".equals(mime) && (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"));
        boolean png = "image/png".equals(mime) && fileName.endsWith(".png");
        boolean webp = "image/webp".equals(mime) && fileName.endsWith(".webp");
        if (!(jpeg || png || webp)) {
            throw new FileUploadException("Selecciona una imagen JPG, PNG o WEBP.");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] signature = in.readNBytes(12);
            boolean valid = (jpeg && signature.length >= 3 &&
                    (signature[0] & 0xff) == 0xff && (signature[1] & 0xff) == 0xd8 &&
                    (signature[2] & 0xff) == 0xff)
                    || (png && signature.length >= 8 &&
                    (signature[0] & 0xff) == 0x89 && signature[1] == 0x50 &&
                    signature[2] == 0x4e && signature[3] == 0x47 &&
                    signature[4] == 0x0d && signature[5] == 0x0a &&
                    signature[6] == 0x1a && signature[7] == 0x0a)
                    || (webp && signature.length >= 12 &&
                    signature[0] == 'R' && signature[1] == 'I' &&
                    signature[2] == 'F' && signature[3] == 'F' &&
                    signature[8] == 'W' && signature[9] == 'E' &&
                    signature[10] == 'B' && signature[11] == 'P');
            if (!valid) throw new FileUploadException("El contenido del archivo no es una imagen válida.");
        } catch (IOException e) {
            throw new FileUploadException("No se pudo leer la imagen.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty())
            throw new FileUploadException("El archivo está vacío");
        if (file.getSize() > getMaxFileSize())
            throw new FileUploadException(
                    "El archivo excede el tamaño máximo permitido de " + fileProperties.getMaxSize());

        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();

        if (originalName == null
                || fileProperties.getAllowedExtensions().stream().noneMatch(originalName.toLowerCase()::endsWith)) {
            throw new FileUploadException("Extensión no permitida: " + originalName);
        }

        if (contentType == null ||
                (!contentType.startsWith("image") &&
                        !contentType.startsWith("video") &&
                        !contentType.startsWith("audio") &&
                        !contentType.equals("application/pdf"))) {
            throw new FileUploadException("Tipo de archivo no permitido: " + contentType);
        }
    }

    private void validateFolder(String folder) {
        if (!fileProperties.getAllowedFolders().contains(folder))
            throw new FileUploadException("Carpeta no permitida: " + folder);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));
    }

    Double getMediaDuration(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.getAbsolutePath());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }
            process.waitFor();

            if (output != null)
                return Double.parseDouble(output.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String calculateChecksum(InputStream is) throws IOException {
        try (is) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no soportado", e);
        }
    }

    public String getFileUrl(Long fileId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException("Archivo no encontrado con id: " + fileId));
        return file.getUrl();
    }

    @Transactional
    public void deleteFileByUrl(String url) {
        if (url == null || url.isBlank())
            return;

        fileRepository.findByUrl(url).ifPresent(file -> {
            try {
                s3Service.deleteFile(file.getKey());
                fileRepository.delete(file);
            } catch (Exception e) {
                throw new RuntimeException("Error eliminando archivo de AWS: " + e.getMessage());
            }
        });
    }

}
