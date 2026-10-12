package com.contractvault.service;

import com.contractvault.exception.DocumentValidationException;
import com.contractvault.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${contractvault.storage.location:./storage}")
    private String configuredLocation;

    private Path rootStoragePath;

    @PostConstruct
    public void init() {
        Path path = Paths.get(configuredLocation);
        // If configured as ./storage but running from backend subfolder, locate parent storage directory
        if (!Files.exists(path) && Files.exists(Paths.get("../storage"))) {
            path = Paths.get("../storage");
        }
        this.rootStoragePath = path.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStoragePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local document storage directory: " + this.rootStoragePath, e);
        }
    }

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentValidationException("Uploaded file cannot be empty");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        if (originalFilename.contains("..")) {
            throw new DocumentValidationException("Invalid filename containing directory traversal sequence: " + originalFilename);
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }

        // Generate safe unique server-side storage key
        String storageKey = UUID.randomUUID().toString() + extension.toLowerCase();
        Path destinationFile = this.rootStoragePath.resolve(storageKey).normalize();

        if (!destinationFile.getParent().equals(this.rootStoragePath)) {
            throw new DocumentValidationException("Cannot store file outside current storage directory");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded document on server", e);
        }

        return storageKey;
    }

    public Path getFilePath(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new ResourceNotFoundException("Storage key is null or empty");
        }

        Path file = this.rootStoragePath.resolve(storageKey).normalize();
        if (!file.startsWith(this.rootStoragePath) || !Files.exists(file)) {
            throw new ResourceNotFoundException("Stored document not found on server");
        }

        return file;
    }

    public void deleteFile(String storageKey) {
        if (StringUtils.hasText(storageKey)) {
            try {
                Path file = this.rootStoragePath.resolve(storageKey).normalize();
                if (file.startsWith(this.rootStoragePath)) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException ignored) {
                // Best effort rollback deletion
            }
        }
    }

    public Path getRootStoragePath() {
        return rootStoragePath;
    }
}
