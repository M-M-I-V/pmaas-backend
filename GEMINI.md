# eClinic Project Overview

This is a Spring Boot 3 REST API for the **eClinic** management system. It provides comprehensive functionality for managing patients, medical and dental visits, and audit logging.

## Core Technology Stack

- **Backend:** Java 21, Spring Boot 3.4.9
- **Security:** Spring Security (Stateless, JWT-based via `jjwt`)
- **Persistence:** Spring Data JPA with MySQL
- **Utilities:** Project Lombok, Apache Commons CSV
- **Build System:** Maven (`mvnw` wrapper included)

## Key Project Features

- **Patient Management:** CRUD operations, CSV import/export, and search.
- **Visit Tracking:** Specialized tracking for Medical and Dental visits.
- **Dashboard:** Analytics for visit trends and diagnosis statistics.
- **Audit Logging:** Systematic recording of entity updates and sensitive actions.
- **Role-Based Access Control:** Predefined roles including `MD` (Medical Doctor), `DMD` (Dentist), and `NURSE`.
- **File Uploads:** Support for uploading and serving attachments (stored in the `/uploads` directory).

## Architecture

The project follows a standard layered architecture:
- **`controller/`**: REST endpoints with `@PreAuthorize` security checks.
- **`service/`**: Business logic layer (includes specialized services like `AuditLogService`, `JWTService`).
- **`repository/`**: Spring Data JPA repositories for database interaction.
- **`entity/`**: JPA entities representing the database schema.
- **`dto/`**: Data Transfer Objects for request/response payloads.
- **`configuration/`**: Web security and upload configurations.
- **`filter/`**: Custom filters like `JWTFilter` for authentication.

## Building and Running

### Prerequisites
- **Java 21**
- **MySQL** (Database: `mcst_clinic`)

### Commands
- **Build:** `./mvnw clean install`
- **Run:** `./mvnw spring-boot:run`
- **Test:** `./mvnw test`

## Development Conventions

- **Security:** Always use `@PreAuthorize` on controller methods to enforce role-based access.
- **Auditing:** Use `AuditLogService.record(...)` when performing updates on critical entities (e.g., `Patients`).
- **Data Transfer:** Use DTOs for incoming request bodies instead of raw entities to prevent mass assignment vulnerabilities.
- **CORS:** Configured to allow requests from `http://localhost:3000` (typical frontend development port).
- **Lombok:** Heavily utilized; ensure your IDE has the Lombok plugin and annotation processing enabled.

## Key Files
- `pom.xml`: Project dependencies and build configuration.
- `src/main/resources/application.properties`: Database connection and basic security credentials.
- `src/main/java/dev/mmiv/clinic/configuration/WebSecurityConfiguration.java`: Core security and CORS setup.

## 🧑‍💻 Development Conventions & Engineering Standards

Contributors must adhere to the following architectural and coding standards:

### 1. Modern Java & Spring Boot
* **DTO Immutability:** Use Java `record` for all DTOs (Requests, Responses, Projections).
* **Dependency Injection:** Mandate constructor injection via Lombok's `@RequiredArgsConstructor`. **No `@Autowired` on fields.**
* **External Calls:** Use the Spring Boot 3 `RestClient` interface. Avoid `RestTemplate`.

### 2. Security & Validation
* **Strict Validation:** Use Jakarta Bean Validation (`@Valid`, `@NotBlank`, etc.) on all DTOs.
* **File Upload Safety:** Verify MIME-types, enforce strict size limits via properties, and sanitize/randomize filenames (e.g., using UUIDs) to prevent path traversal.
* **Mass Assignment Prevention:** Always bind request bodies to DTOs, never directly to JPA Entities.

### 3. Data Handling & Performance
* **Transactional Boundaries:** Keep `@Transactional` at the service layer. Use `@Transactional(readOnly = true)` for pure fetch operations.
* **Pagination:** Endpoints returning collections (e.g., audit logs, patient search) must utilize Spring Data `Pageable` and return `Page<T>` to prevent Out-Of-Memory (OOM) errors.

### 4. API & Error Handling
* **Thin Controllers:** Controllers handle HTTP transport; services handle business logic.
* **Standardized Errors:** The API implements the RFC 7807 **Problem Details** specification via `@RestControllerAdvice` for uniform JSON error responses.
* **Safe Auditing:** Use `AuditLogService.record()` for critical updates. Avoid logging sensitive PII/PHI or raw JWT tokens in standard application logs.