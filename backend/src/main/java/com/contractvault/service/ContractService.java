package com.contractvault.service;

import com.contractvault.dto.ContractResponse;
import com.contractvault.dto.CreateContractRequest;
import com.contractvault.exception.DocumentValidationException;
import com.contractvault.exception.ResourceNotFoundException;
import com.contractvault.model.Contract;
import com.contractvault.model.ContractStatus;
import com.contractvault.repository.ContractRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
public class ContractService {

    public record StoredDocument(Path filePath, String originalFilename) {}

    private final ContractRepository contractRepository;
    private final StorageService storageService;
    private final DocumentParserService documentParserService;

    public ContractService(ContractRepository contractRepository,
                           StorageService storageService,
                           DocumentParserService documentParserService) {
        this.contractRepository = contractRepository;
        this.storageService = storageService;
        this.documentParserService = documentParserService;
    }

    /**
     * Upload and create a contract with a PDF/DOCX file and text extraction.
     */
    @Transactional
    public ContractResponse createContractWithDocument(String title, MultipartFile file) {
        if (!StringUtils.hasText(title)) {
            throw new DocumentValidationException("Contract title is required and cannot be blank");
        }
        if (title.trim().length() > 255) {
            throw new DocumentValidationException("Contract title cannot exceed 255 characters");
        }
        if (file == null || file.isEmpty()) {
            throw new DocumentValidationException("Uploaded file cannot be empty. Please select a valid contract document file to upload.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        documentParserService.validateExtension(originalFilename);

        // 1. Save file in storage
        String storageKey = storageService.storeFile(file);

        // 2. Extract text with consistency guarantee (cleanup file on error)
        try {
            Path filePath = storageService.getFilePath(storageKey);
            String extractedText = documentParserService.extractText(filePath, originalFilename);

            // 3. Persist contract record in database
            Contract contract = new Contract(title.trim(), originalFilename, storageKey, extractedText);
            Contract saved = contractRepository.save(contract);

            return ContractResponse.fromEntity(saved);
        } catch (Exception ex) {
            // Rollback stored file if parsing or database persistence fails
            storageService.deleteFile(storageKey);
            throw ex;
        }
    }

    /**
     * Update contract status with strict state machine transition validation.
     * Permitted transitions:
     *   DRAFT -> REVIEW
     *   REVIEW -> APPROVED
     *   APPROVED -> REVIEW
     *   REVIEW -> DRAFT
     * Direct step-skipping (e.g. DRAFT -> APPROVED) is rejected.
     */
    @Transactional
    public ContractResponse updateContractStatus(Long id, ContractStatus newStatus) {
        if (newStatus == null) {
            throw new DocumentValidationException("Requested status is required and must be DRAFT, REVIEW, or APPROVED");
        }

        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));

        ContractStatus currentStatus = contract.getStatus();
        if (currentStatus == newStatus) {
            return ContractResponse.fromEntity(contract);
        }

        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new DocumentValidationException(String.format(
                "Invalid status transition from %s to %s. Permitted next transitions: %s",
                currentStatus, newStatus, getPermittedTransitionsDescription(currentStatus)
            ));
        }

        contract.setStatus(newStatus);
        Contract saved = contractRepository.save(contract);
        return ContractResponse.fromEntity(saved);
    }

    /**
     * Retrieve stored document file path and original filename safely.
     */
    @Transactional(readOnly = true)
    public StoredDocument getContractDocument(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));

        if (!StringUtils.hasText(contract.getStorageKey()) || !StringUtils.hasText(contract.getOriginalFilename())) {
            throw new ResourceNotFoundException("No document file is attached to contract #" + id);
        }

        Path filePath = storageService.getFilePath(contract.getStorageKey());
        return new StoredDocument(filePath, contract.getOriginalFilename());
    }

    public static boolean isValidStatusTransition(ContractStatus current, ContractStatus target) {
        return switch (current) {
            case DRAFT -> target == ContractStatus.REVIEW;
            case REVIEW -> target == ContractStatus.APPROVED || target == ContractStatus.DRAFT;
            case APPROVED -> target == ContractStatus.REVIEW;
        };
    }

    public static String getPermittedTransitionsDescription(ContractStatus current) {
        return switch (current) {
            case DRAFT -> "REVIEW";
            case REVIEW -> "DRAFT, APPROVED";
            case APPROVED -> "REVIEW";
        };
    }

    /**
     * Retained for Phase 2 backward compatibility (metadata-only creation).
     */
    @Transactional
    public ContractResponse createContract(CreateContractRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new DocumentValidationException("Contract title is required and cannot be blank");
        }
        Contract contract = new Contract();
        contract.setTitle(request.getTitle().trim());
        Contract saved = contractRepository.save(contract);
        return ContractResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> getAllContracts() {
        return contractRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse getContractById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        return ContractResponse.fromEntity(contract);
    }

    @Transactional(readOnly = true)
    public Contract getContractEntityById(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
    }

    @Transactional
    public void deleteContract(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));

        if (StringUtils.hasText(contract.getStorageKey())) {
            storageService.deleteFile(contract.getStorageKey());
        }

        contractRepository.delete(contract);
    }
}
