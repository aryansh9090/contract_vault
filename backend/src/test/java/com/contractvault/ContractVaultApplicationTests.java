package com.contractvault;

import com.contractvault.dto.ContractResponse;
import com.contractvault.dto.CreateContractRequest;
import com.contractvault.exception.ErrorResponse;
import com.contractvault.model.Contract;
import com.contractvault.model.ContractStatus;
import com.contractvault.repository.ContractRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ContractVaultApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractVaultApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ContractRepository contractRepository;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private byte[] createSamplePdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createEmptyPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createSampleDocx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void contextLoads() {
        assertNotNull(contractRepository);
    }

    @Test
    void healthEndpointContinuesToWork() {
        @SuppressWarnings("unchecked")
        Map<String, String> response = restClient.get()
                .uri("/api/health")
                .retrieve()
                .body(Map.class);

        assertNotNull(response);
        assertEquals("UP", response.get("status"));
        assertEquals("ContractVault API", response.get("application"));
    }

    @Test
    void uploadValidPdfExtractsTextAndSavesContract() throws Exception {
        byte[] pdfBytes = createSamplePdf("Confidential Supplier Agreement. Payment deadline is 30 days.");

        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "supplier_agreement.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Hospital Medical Supplier Contract");
        body.add("file", resource);

        ResponseEntity<ContractResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(201), response.getStatusCode());
        ContractResponse contract = response.getBody();
        assertNotNull(contract);
        assertNotNull(contract.getId());
        assertEquals("Hospital Medical Supplier Contract", contract.getTitle());
        assertEquals("supplier_agreement.pdf", contract.getOriginalFilename());
        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        assertNotNull(contract.getCreatedAt());

        // Verify database entity has stored extracted text and storage key
        Contract savedEntity = contractRepository.findById(contract.getId()).orElse(null);
        assertNotNull(savedEntity);
        assertNotNull(savedEntity.getStorageKey());
        assertNotNull(savedEntity.getExtractedText());
        assertTrue(savedEntity.getExtractedText().contains("Payment deadline is 30 days"));
    }

    @Test
    void uploadValidDocxExtractsTextAndSavesContract() throws Exception {
        byte[] docxBytes = createSampleDocx("Software License Agreement between Party A and Party B.");

        ByteArrayResource resource = new ByteArrayResource(docxBytes) {
            @Override
            public String getFilename() {
                return "license_contract.docx";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Enterprise Software License");
        body.add("file", resource);

        ResponseEntity<ContractResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(201), response.getStatusCode());
        ContractResponse contract = response.getBody();
        assertNotNull(contract);
        assertEquals("license_contract.docx", contract.getOriginalFilename());

        Contract savedEntity = contractRepository.findById(contract.getId()).orElse(null);
        assertNotNull(savedEntity);
        assertTrue(savedEntity.getExtractedText().contains("Software License Agreement"));
    }

    @Test
    void uploadEmptyFileIsRejected() {
        ByteArrayResource emptyResource = new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "empty.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Empty Contract");
        body.add("file", emptyResource);

        ResponseEntity<ErrorResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().toLowerCase().contains("empty"));
    }

    @Test
    void uploadUnsupportedExtensionIsRejected() {
        ByteArrayResource txtResource = new ByteArrayResource("Some text file".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "readme.txt";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Text Document");
        body.add("file", txtResource);

        ResponseEntity<ErrorResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Unsupported"));
    }

    @Test
    void uploadCorruptPdfIsRejected() {
        ByteArrayResource corruptResource = new ByteArrayResource("not a real pdf content".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "fake.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Corrupt Contract");
        body.add("file", corruptResource);

        ResponseEntity<ErrorResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("PDF"));
    }

    @Test
    void uploadPdfWithNoTextSucceedsButHasEmptyExtractedText() throws Exception {
        byte[] emptyPdfBytes = createEmptyPdf();

        ByteArrayResource emptyPdfResource = new ByteArrayResource(emptyPdfBytes) {
            @Override
            public String getFilename() {
                return "scanned_empty.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Scanned Contract Without OCR");
        body.add("file", emptyPdfResource);

        ResponseEntity<ContractResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ContractResponse.class);

        // Scanned PDFs now upload successfully (no rejection)
        assertEquals(HttpStatusCode.valueOf(201), response.getStatusCode());
        ContractResponse contract = response.getBody();
        assertNotNull(contract);
        assertNotNull(contract.getId());
        assertEquals("Scanned Contract Without OCR", contract.getTitle());
        assertEquals("scanned_empty.pdf", contract.getOriginalFilename());
        // extractedText should be empty/null for a scanned PDF
        assertTrue(contract.getExtractedText() == null || contract.getExtractedText().isBlank());
    }

    @Test
    void uploadWithBlankTitleIsRejected() throws Exception {
        byte[] pdfBytes = createSamplePdf("Sample text");

        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "contract.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "   ");
        body.add("file", resource);

        ResponseEntity<ErrorResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
    }

    @Test
    void jsonCreateContractMetadataContinuesToWork() {
        CreateContractRequest request = new CreateContractRequest("Metadata Only Agreement");

        ResponseEntity<ContractResponse> response = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(201), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Metadata Only Agreement", response.getBody().getTitle());
        assertEquals(ContractStatus.DRAFT, response.getBody().getStatus());
    }

    @Test
    void getAllContractsAndGetByIdContinueToWork() {
        CreateContractRequest req = new CreateContractRequest("Searchable Contract");
        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);

        ResponseEntity<List<ContractResponse>> allResponse = restClient.get()
                .uri("/api/contracts")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<ContractResponse>>() {});

        assertEquals(HttpStatusCode.valueOf(200), allResponse.getStatusCode());
        assertNotNull(allResponse.getBody());
        assertTrue(allResponse.getBody().stream().anyMatch(c -> c.getId().equals(created.getId())));

        ResponseEntity<ContractResponse> singleResponse = restClient.get()
                .uri("/api/contracts/{id}", created.getId())
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), singleResponse.getStatusCode());
        assertNotNull(singleResponse.getBody());
        assertEquals(created.getId(), singleResponse.getBody().getId());
    }

    @Test
    void statusTransitionsWorkForwardAndBackwardAndPersist() {
        // Create initial draft contract
        CreateContractRequest req = new CreateContractRequest("Lifecycle Contract");
        ContractResponse initial = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(initial);
        assertEquals(ContractStatus.DRAFT, initial.getStatus());
        Long id = initial.getId();

        // 1. DRAFT -> REVIEW
        ResponseEntity<ContractResponse> toReview = restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "REVIEW"))
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), toReview.getStatusCode());
        assertNotNull(toReview.getBody());
        assertEquals(ContractStatus.REVIEW, toReview.getBody().getStatus());

        // Verify persistence via GET
        ContractResponse fetchedReview = restClient.get()
                .uri("/api/contracts/{id}", id)
                .retrieve()
                .body(ContractResponse.class);
        assertEquals(ContractStatus.REVIEW, fetchedReview.getStatus());

        // 2. REVIEW -> APPROVED
        ResponseEntity<ContractResponse> toApproved = restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "APPROVED"))
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), toApproved.getStatusCode());
        assertEquals(ContractStatus.APPROVED, toApproved.getBody().getStatus());

        // 3. Backward: APPROVED -> REVIEW
        ResponseEntity<ContractResponse> backToReview = restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "REVIEW"))
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), backToReview.getStatusCode());
        assertEquals(ContractStatus.REVIEW, backToReview.getBody().getStatus());

        // 4. Backward: REVIEW -> DRAFT
        ResponseEntity<ContractResponse> backToDraft = restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "DRAFT"))
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), backToDraft.getStatusCode());
        assertEquals(ContractStatus.DRAFT, backToDraft.getBody().getStatus());

        // 5. Idempotent: DRAFT -> DRAFT
        ResponseEntity<ContractResponse> sameDraft = restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "DRAFT"))
                .retrieve()
                .toEntity(ContractResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), sameDraft.getStatusCode());
        assertEquals(ContractStatus.DRAFT, sameDraft.getBody().getStatus());
    }

    @Test
    void invalidStatusTransitionsAreRejectedWithBadRequest() {
        CreateContractRequest req = new CreateContractRequest("Invalid Transition Contract");
        ContractResponse initial = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(initial);
        Long id = initial.getId();

        // 1. DRAFT -> APPROVED (skipping REVIEW is disallowed)
        ResponseEntity<ErrorResponse> skipResponse = restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "APPROVED"))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), skipResponse.getStatusCode());
        assertNotNull(skipResponse.getBody());
        assertTrue(skipResponse.getBody().getMessage().contains("Invalid status transition from DRAFT to APPROVED"));

        // Move to REVIEW, then APPROVED
        restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "REVIEW"))
                .retrieve()
                .toBodilessEntity();

        restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "APPROVED"))
                .retrieve()
                .toBodilessEntity();

        // 2. APPROVED -> DRAFT (skipping backwards is disallowed)
        ResponseEntity<ErrorResponse> backwardSkipResponse = restClient.patch()
                .uri("/api/contracts/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "DRAFT"))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), backwardSkipResponse.getStatusCode());
        assertNotNull(backwardSkipResponse.getBody());
        assertTrue(backwardSkipResponse.getBody().getMessage().contains("Invalid status transition from APPROVED to DRAFT"));
    }

    @Test
    void statusUpdateReturns404ForNonexistentContract() {
        ResponseEntity<ErrorResponse> response = restClient.patch()
                .uri("/api/contracts/{id}/status", 999999L)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "REVIEW"))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Contract not found"));
    }

    @Test
    void statusUpdateReturns400ForInvalidStatusValue() {
        CreateContractRequest req = new CreateContractRequest("Invalid Enum Contract");
        ContractResponse initial = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(initial);

        ResponseEntity<ErrorResponse> response = restClient.patch()
                .uri("/api/contracts/{id}/status", initial.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "NON_EXISTENT_STATUS"))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Invalid status value"));
    }

    @Test
    void getContractDocumentReturnsInlinePdf() throws Exception {
        byte[] pdfBytes = createSamplePdf("Document Viewing Agreement Content");
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "viewable_sample.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Viewable PDF Contract");
        body.add("file", resource);

        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);

        ResponseEntity<byte[]> docResponse = restClient.get()
                .uri("/api/contracts/{id}/document", created.getId())
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatusCode.valueOf(200), docResponse.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, docResponse.getHeaders().getContentType());
        String disposition = docResponse.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("inline"));
        assertTrue(disposition.contains("viewable_sample.pdf"));
        assertNotNull(docResponse.getBody());
        assertEquals(pdfBytes.length, docResponse.getBody().length);
    }

    @Test
    void getContractDocumentReturnsAttachmentDocx() throws Exception {
        byte[] docxBytes = createSampleDocx("Document Viewing Docx Content");
        ByteArrayResource resource = new ByteArrayResource(docxBytes) {
            @Override
            public String getFilename() {
                return "downloadable_sample.docx";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Downloadable DOCX Contract");
        body.add("file", resource);

        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);

        ResponseEntity<byte[]> docResponse = restClient.get()
                .uri("/api/contracts/{id}/document", created.getId())
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatusCode.valueOf(200), docResponse.getStatusCode());
        assertTrue(docResponse.getHeaders().getContentType().toString().contains("wordprocessingml"));
        String disposition = docResponse.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment"));
        assertTrue(disposition.contains("downloadable_sample.docx"));
        assertNotNull(docResponse.getBody());
        assertEquals(docxBytes.length, docResponse.getBody().length);
    }

    @Test
    void getContractDocumentReturns404WhenNoDocumentAttached() {
        CreateContractRequest req = new CreateContractRequest("No Document Contract");
        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);

        ResponseEntity<ErrorResponse> response = restClient.get()
                .uri("/api/contracts/{id}/document", created.getId())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("No document file is attached"));
    }

    @Test
    void getContractDocumentReturns404ForNonexistentContract() {
        ResponseEntity<ErrorResponse> response = restClient.get()
                .uri("/api/contracts/{id}/document", 888888L)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Contract not found"));
    }

    @Test
    void askQuestionOnContractWithExtractedTextFindsDurationAndPayment() throws Exception {
        String docText = "This Master Supply Agreement is entered into on January 1, 2026. " +
                "The agreement shall remain valid for two years from the effective date. " +
                "Payment shall be made within 30 days of receipt of the invoice. " +
                "Either party may terminate upon 60 days written notice.";

        byte[] pdfBytes = createSamplePdf(docText);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "legal_agreement.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Legal Master Supply Agreement");
        body.add("file", resource);

        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);
        Long id = created.getId();

        // 1. Duration question
        ResponseEntity<com.contractvault.dto.QuestionResponse> durationResp = restClient.post()
                .uri("/api/contracts/{id}/ask", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What is the duration of this contract?"))
                .retrieve()
                .toEntity(com.contractvault.dto.QuestionResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), durationResp.getStatusCode());
        assertNotNull(durationResp.getBody());
        assertTrue(durationResp.getBody().isFound());
        assertEquals(id, durationResp.getBody().getContractId());
        assertTrue(durationResp.getBody().getAnswer().toLowerCase().contains("two years"));
        assertNotNull(durationResp.getBody().getSourceExcerpt());

        // 2. Payment question
        ResponseEntity<com.contractvault.dto.QuestionResponse> paymentResp = restClient.post()
                .uri("/api/contracts/{id}/ask", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "When must payment be made?"))
                .retrieve()
                .toEntity(com.contractvault.dto.QuestionResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), paymentResp.getStatusCode());
        assertNotNull(paymentResp.getBody());
        assertTrue(paymentResp.getBody().isFound());
        assertTrue(paymentResp.getBody().getAnswer().toLowerCase().contains("30 days"));

        // 3. Unrelated question returning found=false
        ResponseEntity<com.contractvault.dto.QuestionResponse> unrelatedResp = restClient.post()
                .uri("/api/contracts/{id}/ask", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What is the color of the astronaut spaceship?"))
                .retrieve()
                .toEntity(com.contractvault.dto.QuestionResponse.class);

        assertEquals(HttpStatusCode.valueOf(200), unrelatedResp.getStatusCode());
        assertNotNull(unrelatedResp.getBody());
        assertFalse(unrelatedResp.getBody().isFound());
        assertTrue(unrelatedResp.getBody().getAnswer().contains("couldn't find a relevant answer"));
    }

    @Test
    void askQuestionReturns404ForNonexistentContract() {
        ResponseEntity<ErrorResponse> response = restClient.post()
                .uri("/api/contracts/{id}/ask", 999999L)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What is the duration?"))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Contract not found"));
    }

    @Test
    void askQuestionReturnsBadRequestForContractWithoutDocument() {
        CreateContractRequest req = new CreateContractRequest("No Document QA Contract");
        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);

        ResponseEntity<ErrorResponse> response = restClient.post()
                .uri("/api/contracts/{id}/ask", created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What is the duration?"))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("no attached document") || response.getBody().getMessage().contains("no extracted document text"));
    }

    @Test
    void askQuestionRejectsBlankAndExcessivelyLongQuestion() throws Exception {
        byte[] pdfBytes = createSamplePdf("General Agreement Terms and Conditions.");
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "terms.pdf";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Terms Contract");
        body.add("file", resource);

        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);
        Long id = created.getId();

        // 1. Blank question
        ResponseEntity<ErrorResponse> blankResp = restClient.post()
                .uri("/api/contracts/{id}/ask", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "   "))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), blankResp.getStatusCode());

        // 2. Excessively long question (>500 characters)
        String longQuestion = "a".repeat(505);
        ResponseEntity<ErrorResponse> longResp = restClient.post()
                .uri("/api/contracts/{id}/ask", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", longQuestion))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(400), longResp.getStatusCode());
    }

    @Test
    void askQuestionEnsuresAnswerBelongsToRequestedContract() throws Exception {
        byte[] pdf1 = createSamplePdf("Contract Alpha specifies the duration is three years.");
        ByteArrayResource res1 = new ByteArrayResource(pdf1) {
            @Override public String getFilename() { return "alpha.pdf"; }
        };
        MultiValueMap<String, Object> body1 = new LinkedMultiValueMap<>();
        body1.add("title", "Alpha Contract");
        body1.add("file", res1);
        ContractResponse c1 = restClient.post().uri("/api/contracts").contentType(MediaType.MULTIPART_FORM_DATA).body(body1).retrieve().body(ContractResponse.class);

        byte[] pdf2 = createSamplePdf("Contract Beta specifies the duration is five years.");
        ByteArrayResource res2 = new ByteArrayResource(pdf2) {
            @Override public String getFilename() { return "beta.pdf"; }
        };
        MultiValueMap<String, Object> body2 = new LinkedMultiValueMap<>();
        body2.add("title", "Beta Contract");
        body2.add("file", res2);
        ContractResponse c2 = restClient.post().uri("/api/contracts").contentType(MediaType.MULTIPART_FORM_DATA).body(body2).retrieve().body(ContractResponse.class);

        assertNotNull(c1);
        assertNotNull(c2);

        com.contractvault.dto.QuestionResponse resp1 = restClient.post()
                .uri("/api/contracts/{id}/ask", c1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What is the duration?"))
                .retrieve()
                .body(com.contractvault.dto.QuestionResponse.class);

        com.contractvault.dto.QuestionResponse resp2 = restClient.post()
                .uri("/api/contracts/{id}/ask", c2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What is the duration?"))
                .retrieve()
                .body(com.contractvault.dto.QuestionResponse.class);

        assertNotNull(resp1);
        assertNotNull(resp2);
        assertTrue(resp1.getAnswer().toLowerCase().contains("three years"));
        assertTrue(resp2.getAnswer().toLowerCase().contains("five years"));
    }

    @Test
    void deleteContractSuccessfullyRemovesContractAndFile() throws Exception {
        byte[] pdf = createSamplePdf("Sample agreement for deletion testing.");
        ByteArrayResource res = new ByteArrayResource(pdf) {
            @Override public String getFilename() { return "delete_test.pdf"; }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Contract to Delete");
        body.add("file", res);

        ContractResponse created = restClient.post()
                .uri("/api/contracts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(ContractResponse.class);

        assertNotNull(created);
        Long id = created.getId();

        // Verify it exists via GET
        ResponseEntity<ContractResponse> getBefore = restClient.get()
                .uri("/api/contracts/{id}", id)
                .retrieve()
                .toEntity(ContractResponse.class);
        assertEquals(HttpStatusCode.valueOf(200), getBefore.getStatusCode());

        // Perform DELETE
        ResponseEntity<Void> deleteResp = restClient.delete()
                .uri("/api/contracts/{id}", id)
                .retrieve()
                .toBodilessEntity();
        assertEquals(HttpStatusCode.valueOf(204), deleteResp.getStatusCode());

        // Verify GET now returns 404
        ResponseEntity<ErrorResponse> getAfter = restClient.get()
                .uri("/api/contracts/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);
        assertEquals(HttpStatusCode.valueOf(404), getAfter.getStatusCode());
    }

    @Test
    void deleteNonExistentContractReturns404() {
        ResponseEntity<ErrorResponse> resp = restClient.delete()
                .uri("/api/contracts/{id}", 999999L)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (r, s) -> {})
                .toEntity(ErrorResponse.class);

        assertEquals(HttpStatusCode.valueOf(404), resp.getStatusCode());
    }
}


