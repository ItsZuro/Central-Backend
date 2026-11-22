package com.centrral.centralres.core.aws.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import java.time.Duration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.centrral.centralres.core.aws.dto.response.DeleteResponse;
import com.centrral.centralres.core.aws.dto.response.FileResponse;
import com.centrral.centralres.core.aws.service.FileService;
import com.centrral.centralres.core.security.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

        private final FileService fileService;

        @PostMapping("/upload")
        public ResponseEntity<ApiResponse<FileResponse>> uploadFile(
                        @RequestParam("file") MultipartFile file,
                        @RequestParam(value = "folder", defaultValue = "general") String folder) {

                var metadata = fileService.uploadFile(file, folder);

                var details = metadata.getDetails();

                var response = FileResponse.builder()
                                .id(metadata.getId())
                                .fileName(metadata.getOriginalName())
                                .fileUrl(metadata.getUrl())
                                .folder(metadata.getFolder())
                                .uploadedById(metadata.getUploadedBy().getId())
                                .uploadedByUsername(metadata.getUploadedBy().getUsername())
                                .uploadDate(metadata.getUploadDate())
                                .size(details != null ? details.getSize() : null)
                                .contentType(details != null ? details.getContentType() : null)
                                .build();

                return ResponseEntity.ok(
                                new ApiResponse<>(true, "Archivo subido exitosamente", response));
        }

        /** Imagen del catálogo servida desde el bucket privado; no expone credenciales S3. */
        @GetMapping("/product-images/{fileId}")
        public ResponseEntity<byte[]> getProductImage(@PathVariable Long fileId) {
                FileService.ProductImage image = fileService.readProductImage(fileId);
                return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(image.contentType()))
                                .contentLength(image.bytes().length)
                                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                                .header("X-Content-Type-Options", "nosniff")
                                .body(image.bytes());
        }

        @GetMapping("/category-images/{fileId}")
        public ResponseEntity<byte[]> getCategoryImage(@PathVariable Long fileId) {
                FileService.ProductImage image = fileService.readCategoryImage(fileId);
                return imageResponse(image);
        }

        @GetMapping("/profile-images/{fileId}")
        public ResponseEntity<byte[]> getProfileImage(@PathVariable Long fileId) {
                FileService.ProductImage image = fileService.readProfileImage(fileId);
                return imageResponse(image);
        }

        private ResponseEntity<byte[]> imageResponse(FileService.ProductImage image) {
                return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(image.contentType()))
                                .contentLength(image.bytes().length)
                                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                                .header("X-Content-Type-Options", "nosniff")
                                .body(image.bytes());
        }

        @DeleteMapping("/delete/{fileId}")
        public ResponseEntity<ApiResponse<DeleteResponse>> deleteFile(@PathVariable Long fileId) {
                var result = fileService.deleteFile(fileId);

                var response = new DeleteResponse(
                                result.getFileId(),
                                result.getDeletedById(),
                                result.getDeletedByUsername());

                return ResponseEntity.ok(
                                new ApiResponse<>(true, "Archivo eliminado exitosamente", response));
        }
}
