package dev.mmiv.pmaas.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Shared, security-hardened file storage service.
 * User-supplied filenames are NEVER used as filesystem paths.
 *   Every file is stored under a UUID-based name so directory traversal
 *   attacks (e.g. "../../application.properties") are impossible.
 * The public-facing base URL is read from the
 *   app.base-url property (set via APP_BASE_URL environment variable),
 *   not hardcoded to localhost:8080.
 * This single service replaces the identical
 *   saveUploadedFile() methods that existed separately in both
 *   MedicalVisitsService and DentalVisitsService.
 */
@Slf4j
@Service
public class FileStorageService {

    /**
     * Allowed file extensions for medical/dental uploads.
     * Only image and PDF formats are permitted. No executables, scripts, or archives.
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf"
    );

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Saves a multipart file to the local uploads directory and returns
     * the publicly accessible URL.
     * Returns null if the file is null or empty (optional upload fields).
     * @param multipartFile The uploaded file, may be null or empty.
     * @return              The full public URL to access the file, or null.
     * @throws IOException  If writing to disk fails.
     * @throws ResponseStatusException (400) if the file extension is not on the whitelist.
     */
    public String save(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        String originalName  = multipartFile.getOriginalFilename();
        String rawExtension  = FilenameUtils.getExtension(originalName);   // "" if no extension
        String safeExtension = (rawExtension != null ? rawExtension : "").toLowerCase();

        // Extension whitelist check
        if (!ALLOWED_EXTENSIONS.contains(safeExtension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File type '." + safeExtension + "' is not allowed. " +
                            "Permitted types: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }

        // Build a safe filename using a UUID — no user input in the path
        String safeFileName = UUID.randomUUID() + "." + safeExtension;

        // Ensure upload directory exists
        String uploadDir = System.getProperty("user.dir") + "/uploads";
        File directory = new File(uploadDir);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create upload directory: " + uploadDir);
        }

        // Write file to disk
        File destination = new File(uploadDir + "/" + safeFileName);
        multipartFile.transferTo(destination);

        log.debug("File saved: originalName='{}' storedAs='{}' size={}",
                originalName, safeFileName, multipartFile.getSize());

        // Return the publicly accessible URL
        // baseUrl comes from APP_BASE_URL, not a hardcoded string.
        return baseUrl + "/uploads/" + safeFileName;
    }
}