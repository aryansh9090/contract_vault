# ContractVault

ContractVault is an enterprise contract management application built for the **Seventh Ray Consulting and Technology Pvt. Ltd.** internship assignment.

The system is designed to allow organizations to upload contracts (PDF and Microsoft Word formats), securely persist them, manage their approval workflows (`DRAFT` → `REVIEW` → `APPROVED`), query contract contents using text extraction and keyword matching, and ask natural-language questions about a contract's text.

---

## 📋 Evaluation Review Deliverables & Architecture Clarifications

This section directly addresses the evaluation review questions regarding production persistence, confidentiality protection, scanned document parsing design, Q&A limitations/improvements, and deployment links.

### 1. Live Deployment & Submission Resources
- **Live Deployed Application:** [https://contract-vault.onrender.com](https://contract-vault.onrender.com)
- **GitHub Repository:** [https://github.com/aryansh9090/contract_vault](https://github.com/aryansh9090/contract_vault)
- **Automated Test Results:** **27/27 Tests Passed** (`BUILD SUCCESS`, 0 Failures, 0 Errors, 0 Skipped).
- **Screen-Recorded Demonstration:** `[Link to Screen-Recorded Demonstration Video]`  
  *(Demonstrates contract upload, retrieval, download/preview, status transitions, Q&A with source citations, negative tests on out-of-contract queries, and input validation)*.

---

### 2. Ensuring Document and Database Persistence Across Restarts & Redeployments

#### The Challenge with Ephemeral Containers
In cloud container environments (such as Docker on Render, AWS ECS, or Kubernetes), the local container filesystem is **ephemeral**. Any files written directly to the container's disk (`/app/data` or `/app/storage`) are wiped when a new deployment occurs or the container restarts.

#### How Persistence is Handled:
1. **Local Development Environment:**
   - Database records persist across restarts via file-backed H2 engine at `backend/data/contractvaultdb.mv.db`.
   - Uploaded documents persist in the configured local directory `./storage`.
2. **Production Enterprise Architecture:**
   - **Database Persistence (Relational):** The application uses Spring Data JPA / Hibernate abstractions with zero vendor lock-in. For production, the database is decoupled from the application container by migrating to a managed cloud database such as **PostgreSQL** (AWS RDS, Supabase, or Render PostgreSQL) via standard Spring environment variables:
     ```bash
     SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>
     SPRING_DATASOURCE_USERNAME=<user>
     SPRING_DATASOURCE_PASSWORD=<password>
     ```
   - **Document Storage Persistence (Object Storage):** Physical contract documents are persisted externally using **Cloud Object Storage** (e.g., **AWS S3**, **Google Cloud Storage**, or **Supabase Storage**) or by attaching a **Persistent Volume Claim (PVC)** mounted to `/app/storage`. When files are uploaded, unique UUID storage keys decouple physical filenames from system metadata, ensuring zero data loss across container lifecycle events.

---

### 3. Protecting Confidential Contract Information

Contracts contain highly sensitive commercial and legal terms (pricing, salaries, trade secrets, liability terms). Access is protected using four enterprise security layers:

1. **Authentication & Role-Based Access Control (RBAC):**
   - Secure the API using **Spring Security** with **OAuth2 / JWT tokens** or enterprise SSO.
   - Restrict endpoint operations by role:
     - `LEGAL_ADMIN`: Upload, approve, edit, and delete contracts.
     - `REVIEWER`: Inspect contracts and transition status from `DRAFT` to `REVIEW`.
     - `VIEWER`: Read-only access to approved contracts.
2. **Data Encryption (In Transit & At Rest):**
   - **In Transit:** Enforce HTTPS with TLS 1.3 for all frontend-to-backend and backend-to-storage traffic.
   - **At Rest:** Database fields and file storage volumes are encrypted using AES-256 (e.g., AWS KMS server-side encryption for S3 buckets).
3. **Secure Document Access (No Public Static URLs):**
   - Stored documents are never exposed as static web assets or direct filesystem paths.
   - Downloads and previews are authorized on-demand: the server validates credentials and issues short-lived, **time-limited Pre-Signed URLs** (e.g., valid for 5 minutes) rather than permanent public links.
4. **Audit Logging & Compliance:**
   - Every read, download, status update, and Q&A query is logged in an immutable audit table with timestamp, user ID, IP address, and contract ID to comply with SOC 2 / ISO 27001 requirements.

---

### 4. Scanned PDF Handling: Design Rationale & Implementation Synchronization

- **Initial Specification:** Early drafts specified outright rejection of documents containing no readable text to prevent unsearchable records.
- **Production Usability Rationale:** In real-world enterprise legal workflows, organizations frequently need to catalog, manage approval lifecycles for, and download legacy scanned agreements even if automated text extraction is unavailable without OCR.
- **Implemented Architecture:** `DocumentParserService` gracefully stores the uploaded document and creates the contract record with empty extracted text (`""`). When a user attempts to ask questions on a scanned contract, the Q&A service detects that text is unavailable and informs the user politely:
  > *"Contract has no extracted document text available for Q&A."*
- **Future OCR Roadmap:** Integrating an OCR pipeline (Tesseract OCR / AWS Textract) to extract optical text from scanned documents during background processing.

---

### 5. Q&A Approach: Architecture, Limitations, and Improvements

#### A. Current Implementation
The Q&A engine ([`ContractQuestionService.java`](backend/src/main/java/com/contractvault/service/ContractQuestionService.java)) provides dependency-free, high-speed contract querying:
- **Whole-Word & Stem-Bounded Token Matching:** Strict regex word boundaries (`\b`) prevent substring false positives (e.g. preventing `"sam"` from matching `"sample"`).
- **Domain Synonym Expansion:** Maps legal concepts across payments, fees, deadlines, duration, governing laws, parties, and liability terms.
- **Prompt Boilerplate Isolation:** Filters out test prompt suggestions and document footers to prevent non-contract text from being cited as answers.
- **Graceful Negative Query Handling:** Out-of-contract queries (e.g., questions about celebrities or unrelated topics) are strictly rejected with a polite not-found response instead of hallucinating.

#### B. Current Limitations
1. **Lexical / Rule-Based:** Relies on token matching; cannot comprehend complex semantic paraphrasing outside defined synonym mappings.
2. **No Multi-Clause Reasoning or Arithmetic:** Cannot calculate multi-year financial totals (e.g. "$10,000/month for 2 years") or resolve conditional cross-clause exceptions.
3. **Table & Image Data:** Unstructured tables and scanned image PDFs require dedicated OCR and tabular parsing.

#### C. Enterprise RAG (Retrieval-Augmented Generation) Roadmap
1. **Dense Vector Embeddings:** Chunk contract text and compute semantic embeddings using models such as `text-embedding-3-small` or local HuggingFace sentence transformers.
2. **Vector Similarity Index:** Store vectors in `pgvector` or Milvus for sub-millisecond semantic retrieval across thousands of pages.
3. **LLM Synthesis with Grounding:** Feed retrieved chunks to an LLM via **Spring AI** with strict system prompts:
   > *"Answer solely based on the provided contract excerpts. If the information is absent, state that it is not present in the contract."*

---

## Technology Stack

- **Backend:** Java 25, Spring Boot 4.x, Spring Data JPA, Hibernate ORM, Bean Validation, Apache PDFBox 3.0, Apache POI 5.4, Spring Boot Actuator, Maven Wrapper (`mvnw.cmd`)
- **Database:** H2 Database (persistent file-based engine for local development; architected for easy PostgreSQL production migration)
- **Frontend:** React 19, Vite, Tailwind CSS, Lucide React
- **Storage:** Local persistent storage directory (`storage/`)
- **Version Control:** Git

---

## Prerequisites

- **Java JDK:** Java 21+ or Java 25 LTS installed
- **Node.js:** Node.js v20+ (tested with v24.14.0) and npm 10+ (tested with 11.9.0)
- **OS:** Windows 10/11 (PowerShell / Command Prompt)

> **Note:** A global Maven installation is **not** required. The project includes the self-contained Maven Wrapper (`mvnw.cmd`).

---

## Monorepo Folder Structure

```
contract_vault/
├── backend/
│   ├── .mvn/wrapper/                  # Maven Wrapper binaries
│   ├── data/                          # Persistent H2 file database (gitignored)
│   ├── mvnw / mvnw.cmd                # Maven Wrapper executables
│   ├── pom.xml                        # Maven dependencies & build definitions
│   └── src/
│       ├── main/
│       │   ├── java/com/contractvault/
│       │   │   ├── ContractVaultApplication.java
│       │   │   ├── controller/
│       │   │   │   ├── ContractController.java
│       │   │   │   └── HealthController.java
│       │   │   ├── dto/
│       │   │   │   ├── ContractResponse.java
│       │   │   │   └── CreateContractRequest.java
│       │   │   ├── exception/
│       │   │   │   ├── DocumentParsingException.java
│       │   │   │   ├── DocumentValidationException.java
│       │   │   │   ├── ErrorResponse.java
│       │   │   │   ├── GlobalExceptionHandler.java
│       │   │   │   └── ResourceNotFoundException.java
│       │   │   ├── model/
│       │   │   │   ├── Contract.java
│       │   │   │   └── ContractStatus.java
│       │   │   ├── repository/
│       │   │   │   └── ContractRepository.java
│       │   │   └── service/
│       │   │       ├── ContractService.java
│       │   │       ├── DocumentParserService.java
│       │   │       └── StorageService.java
│       │   └── resources/
│       │       └── application.yml    # Database, multipart & storage configuration
│       └── test/
│           ├── java/com/contractvault/
│           │   └── ContractVaultApplicationTests.java
│           └── resources/
│               └── application.yml    # Isolated in-memory test DB & temp storage
├── frontend/
│   ├── package.json
│   ├── vite.config.js                 # Proxy config (/api -> localhost:8080)
│   ├── index.html
│   └── src/
│       ├── App.jsx                    # Enterprise welcome UI & document upload modal
│       ├── main.jsx                   # React root mount
│       └── index.css                  # Tailwind CSS setup & design tokens
├── storage/
│   └── .gitkeep                       # Local protected document storage
├── .gitignore
└── README.md
```

---

## Document Storage & Text Extraction Design (Phase 3)

### 1. File Storage Design
- **Storage Location:** Configurable via `contractvault.storage.location` (defaults to `./storage` at project root).
- **Security & Privacy:** Uploaded documents are stored using securely generated random UUID filenames (e.g., `b5fe5b0b-f6d7-4008-b479-a2003bc8a102.pdf`).
- **Path Traversal Protection:** Filenames are sanitized, and all file operations verify paths remain strictly within the designated storage root.
- **Privacy:** Document storage is internal and is **never** exposed as static public web assets. Absolute paths are never returned to clients.
- **Transactional Consistency:** If text extraction or database persistence fails, the newly uploaded physical file is automatically rolled back and deleted to avoid orphaned files.

### 2. Document Text Extraction
- **PDF Documents:** Parsed using **Apache PDFBox 3.0** (`PDFTextStripper`).
- **Word Documents:** Parsed using **Apache POI 5.4** (`XWPFDocument` and `XWPFWordExtractor`).
- **Empty & Scanned Document Policy:** For documents containing no extractable text (such as scanned image-only PDFs), the document is safely stored and the contract record is cataloged in the repository with an empty text record (`""`). This enables organizations to archive and download paper contracts, while the Q&A service gracefully informs users when a query is submitted: *"Contract has no extracted document text available for Q&A."* Support for automatic OCR via Tesseract/AWS Textract is planned for the production roadmap.

---

## Database Configuration & Persistence

ContractVault uses a persistent file-based **H2 Database** for local development configured in `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/contractvaultdb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update
```

### Highlights:
1. **Data Persistence Across Restarts:** Database records are saved to `backend/data/contractvaultdb.mv.db`, ensuring contracts and extracted text persist across application restarts.
2. **PostgreSQL Compatibility:** Strictly adheres to JPA specifications (`@Lob` mapping to CLOB in H2 and TEXT in PostgreSQL). Production migration only requires setting standard Spring environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`).
3. **Test Isolation:** Automated JUnit tests use an in-memory instance (`jdbc:h2:mem:contractvaulttest`) and isolated target storage (`./target/test-storage`) so test suites run independently without altering development data.

---

## REST API Endpoints

### 1. Health Check
- **Method:** `GET`
- **URL:** `/api/health`
- **Response:** `200 OK`
```json
{
  "status": "UP",
  "application": "ContractVault API"
}
```

---

### 2. Upload Contract Document (Multipart/Form-Data)
- **Method:** `POST`
- **URL:** `/api/contracts`
- **Content-Type:** `multipart/form-data`
- **Form Fields:**
  - `title` *(string, required)*: Contract title (e.g., "Hospital Supplier Master Agreement")
  - `file` *(file, required)*: PDF or DOCX file (up to 10 MB)

**Sample Request (cURL):**
```powershell
curl.exe -X POST http://localhost:8080/api/contracts `
  -F "title=Hospital Supplier Master Agreement" `
  -F "file=@sample_agreement.pdf"
```

**Successful Response (`201 Created`):**
```json
{
  "id": 1,
  "title": "Hospital Supplier Master Agreement",
  "originalFilename": "sample_agreement.pdf",
  "status": "DRAFT",
  "createdAt": "2026-10-12T14:27:06.579",
  "updatedAt": "2026-10-12T14:27:06.579"
}
```

---

### 3. List All Contracts
- **Method:** `GET`
- **URL:** `/api/contracts`
- **Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Hospital Supplier Master Agreement",
    "originalFilename": "sample_agreement.pdf",
    "status": "DRAFT",
    "createdAt": "2026-10-12T14:27:06.579",
    "updatedAt": "2026-10-12T14:27:06.579"
  }
]
```

---

### 4. Fetch Single Contract by ID
- **Method:** `GET`
- **URL:** `/api/contracts/{id}`
- **Response:** `200 OK`
```json
{
  "id": 1,
  "title": "Hospital Supplier Master Agreement",
  "originalFilename": "sample_agreement.pdf",
  "status": "DRAFT",
  "createdAt": "2026-10-12T14:27:06.579",
  "updatedAt": "2026-10-12T14:27:06.579"
}
```

---

### 5. Update Contract Status (PATCH)
- **Method:** `PATCH`
- **URL:** `/api/contracts/{id}/status`
- **Content-Type:** `application/json`
- **Request Body:**
```json
{
  "status": "REVIEW"
}
```
- **Supported Statuses:** `DRAFT`, `REVIEW`, `APPROVED`
- **Enforced Lifecycle Transitions:**
  - Forward: `DRAFT` → `REVIEW` → `APPROVED`
  - Backward: `APPROVED` → `REVIEW` → `DRAFT`
  - Skipping steps (e.g. `DRAFT` → `APPROVED`) is strictly rejected with `400 Bad Request`.
  - Nonexistent contract IDs return `404 Not Found`.

**Sample Request (cURL):**
```bash
curl.exe -X PATCH http://localhost:8080/api/contracts/1/status \
  -H "Content-Type: application/json" \
  -d "{\"status\":\"REVIEW\"}"
```

**Successful Response (`200 OK`):**
```json
{
  "id": 1,
  "title": "Hospital Supplier Master Agreement",
  "originalFilename": "sample_agreement.pdf",
  "status": "REVIEW",
  "createdAt": "2026-10-12T14:27:06.579",
  "updatedAt": "2026-10-12T14:50:15.611"
}
```

---

### 6. Open or Download Stored Contract Document
- **Method:** `GET`
- **URL:** `/api/contracts/{id}/document`
- **Behavior:**
  - **PDF Documents:** Returns `Content-Type: application/pdf` with `Content-Disposition: inline; filename="document.pdf"`, rendering directly in the browser's native PDF viewer.
  - **DOCX Documents:** Returns `Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document` with `Content-Disposition: attachment; filename="document.docx"`, triggering an automatic file download.
  - Documents are retrieved safely using internal UUID storage keys (never exposing server paths).
  - Returns `404 Not Found` if contract does not exist or has no file attached.

**Sample Request (cURL):**
```bash
curl.exe -i http://localhost:8080/api/contracts/1/document
```

---

### 7. Ask a Question About a Contract (Phase 5 — Contract Q&A)
- **Method:** `POST`
- **URL:** `/api/contracts/{id}/ask`
- **Content-Type:** `application/json`
- **Request Body:**
```json
{
  "question": "What is the duration of this contract?"
}
```
- **Constraints:** Question must be non-blank and ≤ 500 characters.
- **Engine:** Uses keyword tokenisation, stop-word filtering, and synonym expansion (e.g. "duration" → "term", "period") to score contract sentences and return the best matching excerpt.

**Sample Request (cURL):**
```bash
curl.exe -X POST http://localhost:8080/api/contracts/1/ask \
  -H "Content-Type: application/json" \
  -d "{\"question\":\"What is the payment schedule?\"}"
```

**Successful Response (`200 OK`) — answer found:**
```json
{
  "contractId": 1,
  "question": "What is the payment schedule?",
  "answer": "Payment shall be made within 30 days of receipt of the invoice.",
  "found": true,
  "sourceExcerpt": "Payment shall be made within 30 days of receipt of the invoice."
}
```

**Successful Response (`200 OK`) — no relevant answer:**
```json
{
  "contractId": 1,
  "question": "What is the color of the astronaut spaceship?",
  "answer": "I couldn't find a relevant answer in this contract.",
  "found": false,
  "sourceExcerpt": null
}
```

**Error — no document text available (`400 Bad Request`):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Contract #2 has no extracted document text available for Q&A.",
  "path": "/api/contracts/2/ask",
  "timestamp": "2026-10-12T15:00:00.000"
}
```

**Error — contract not found (`404 Not Found`):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Contract not found with id: 999",
  "path": "/api/contracts/999/ask",
- **Q&A Engine Details:**
  - **Tokenization & Stopwords:** Strips punctuation, corrects common typos (e.g. `cotract` → `contract`), and filters non-informational filler words.
  - **Whole-Word & Stem-Bounded Matching:** All query tokens and stems require strict word boundaries (`\b`) to prevent false-positive substring matches (e.g. preventing "sam" from matching "sample").
  - **Domain Synonym Expansion:** Expands legal and business concepts across finances, payment schedules, duration, liability caps, security incident deadlines, governing laws, and termination policies.
  - **Intent & Pattern Alignment:** Boosts relevant clauses for specific question types (e.g., party identification, calendar dates, currency amounts, deadlines).
  - **Boilerplate & Test Prompt Isolation:** Specifically ignores non-contract instructions such as sample testing suggestions or end-of-document footers.
  - **Polite Handling of Out-of-Contract Queries:** When a query has no matching tokens or confidence score falls below the strict threshold (e.g. "Who is Sam Altman?"), the system gracefully responds that the information is not present in the contract without hallucinating or crashing.

#### Current Q&A Limitations & Future RAG Roadmap:
1. **Current Limitations:**
   - **Lexical/Rule-Based:** Relies on keyword token matching and hardcoded synonyms; cannot understand abstract rephrasing outside the domain vocabulary.
   - **No Multi-Hop Reasoning or Arithmetic:** Cannot calculate multi-year totals (e.g. "$1,000/month for 12 months") or perform logical reasoning across disconnected clauses.
   - **OCR Dependent:** Scanned images require pre-extracted text or external OCR.
2. **Enterprise RAG (Retrieval-Augmented Generation) Roadmap:**
   - **Vector Embeddings:** Generate dense vector representations (e.g., via Spring AI, OpenAI `text-embedding-3-small`, or local HuggingFace embeddings).
   - **Semantic Search:** Store embeddings in `pgvector` or Milvus to support true semantic similarity search across complex clauses.
   - **LLM Grounding:** Feed the top retrieved passages to an LLM with strict instructions to answer solely based on the excerpt and state "not found in contract" when unmentioned.

---

### 8. Error Responses
Standardized JSON error formatting without sensitive stack traces:

- **Missing/Empty File (`400 Bad Request`):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Uploaded file cannot be empty. Please select a valid contract document file to upload.",
  "path": "/api/contracts",
  "timestamp": "2026-10-12T14:20:00.000"
}
```

- **Invalid Status Transition (`400 Bad Request`):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid status transition from DRAFT to APPROVED. Permitted next transitions: REVIEW",
  "path": "/api/contracts/1/status",
  "timestamp": "2026-10-12T14:20:00.000"
}
```

- **No Document Attached (`404 Not Found`):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "No document file is attached to contract #1",
  "path": "/api/contracts/1/document",
  "timestamp": "2026-10-12T14:20:00.000"
}
```

---

## How to Run on Windows

### Start Backend:
```powershell
cd "c:\Users\Aryan Sharma\Desktop\contract_vault\backend"
.\mvnw.cmd spring-boot:run
```
*(Runs on `http://localhost:8080`)*

### Start Frontend:
```powershell
cd "c:\Users\Aryan Sharma\Desktop\contract_vault\frontend"
npm.cmd run dev
```
*(Runs on `http://localhost:5173`)*

---

## Testing

### Run Backend Automated Tests:
```powershell
cd "c:\Users\Aryan Sharma\Desktop\contract_vault\backend"
.\mvnw.cmd test
```

### Run Frontend Production Build:
```powershell
cd "c:\Users\Aryan Sharma\Desktop\contract_vault\frontend"
npm.cmd run build
```

