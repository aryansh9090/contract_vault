package com.contractvault.dto;

import com.contractvault.model.Contract;
import com.contractvault.model.ContractStatus;

import java.time.LocalDateTime;

public class ContractResponse {

    private Long id;
    private String title;
    private String originalFilename;
    private String extractedText;
    private ContractStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ContractResponse() {
    }

    public ContractResponse(Long id, String title, String originalFilename, String extractedText, ContractStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.originalFilename = originalFilename;
        this.extractedText = extractedText;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ContractResponse fromEntity(Contract contract) {
        if (contract == null) {
            return null;
        }
        return new ContractResponse(
                contract.getId(),
                contract.getTitle(),
                contract.getOriginalFilename(),
                contract.getExtractedText(),
                contract.getStatus(),
                contract.getCreatedAt(),
                contract.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
