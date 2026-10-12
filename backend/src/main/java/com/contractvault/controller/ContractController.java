package com.contractvault.controller;

import com.contractvault.dto.ContractResponse;
import com.contractvault.dto.CreateContractRequest;
import com.contractvault.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;
    private final com.contractvault.service.ContractQuestionService contractQuestionService;

    public ContractController(ContractService contractService,
                              com.contractvault.service.ContractQuestionService contractQuestionService) {
        this.contractService = contractService;
        this.contractQuestionService = contractQuestionService;
    }

    /**
     * Multipart endpoint for uploading contract documents (PDF/DOCX).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponse> uploadContract(
            @RequestParam("title") String title,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        ContractResponse created = contractService.createContractWithDocument(title, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * JSON endpoint for creating contract metadata (backward compatibility).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContractResponse> createContractJson(@Valid @RequestBody CreateContractRequest request) {
        ContractResponse created = contractService.createContract(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ContractResponse>> getAllContracts() {
        List<ContractResponse> contracts = contractService.getAllContracts();
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractResponse> getContractById(@PathVariable Long id) {
        ContractResponse contract = contractService.getContractById(id);
        return ResponseEntity.ok(contract);
    }

    /**
     * Update contract status with transition validation (DRAFT <-> REVIEW <-> APPROVED).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ContractResponse> updateContractStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.contractvault.dto.UpdateStatusRequest request) {
        ContractResponse updated = contractService.updateContractStatus(id, request.getStatus());
        return ResponseEntity.ok(updated);
    }

    /**
     * Download or view uploaded contract document.
     * PDFs return inline content-disposition; DOCX returns attachment.
     */
    @GetMapping("/{id}/document")
    public ResponseEntity<org.springframework.core.io.Resource> getContractDocument(@PathVariable Long id) {
        ContractService.StoredDocument doc = contractService.getContractDocument(id);
        org.springframework.core.io.Resource resource;
        try {
            resource = new org.springframework.core.io.UrlResource(doc.filePath().toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new com.contractvault.exception.ResourceNotFoundException("Stored document file is missing or unreadable");
            }
        } catch (java.net.MalformedURLException e) {
            throw new com.contractvault.exception.ResourceNotFoundException("Invalid document file path");
        }

        String filename = doc.originalFilename();
        String lower = filename.toLowerCase();
        MediaType mediaType;
        String disposition;

        if (lower.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
            disposition = org.springframework.http.ContentDisposition.inline()
                    .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                    .build()
                    .toString();
        } else if (lower.endsWith(".docx")) {
            mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            disposition = org.springframework.http.ContentDisposition.attachment()
                    .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                    .build()
                    .toString();
        } else {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
            disposition = org.springframework.http.ContentDisposition.attachment()
                    .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                    .build()
                    .toString();
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    /**
     * Ask a question about an uploaded contract's extracted text.
     */
    @PostMapping("/{id}/ask")
    public ResponseEntity<com.contractvault.dto.QuestionResponse> askQuestion(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.contractvault.dto.QuestionRequest request) {
        com.contractvault.dto.QuestionResponse response = contractQuestionService.answerQuestion(id, request.getQuestion());
        return ResponseEntity.ok(response);
    }

    /**
     * Delete contract and associated physical document.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContract(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ResponseEntity.noContent().build();
    }
}


