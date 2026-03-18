package dev.mmiv.pmaas.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves uploaded files (medical/dental images, PDFs) over an authenticated endpoint.
 * WHY THIS EXISTS — replaces WebUploadConfiguration.java:
 *   WebUploadConfiguration registered /uploads/** as a Spring MVC static resource handler.
 *   Static resource handlers resolve files outside the Spring Security filter chain,
 *   meaning the /uploads/** permit rule in WebSecurityConfiguration (S-13 fix) was
 *   silently bypassed — unauthenticated requests could still retrieve PHI files.
 *   This controller sits inside the filter chain. The JWTFilter runs first, validates
 *   the Bearer token, and only then does DispatcherServlet route to this handler.
 *   No additional @PreAuthorize is needed — anyRequest().authenticated() in
 *   WebSecurityConfiguration covers this endpoint.
 * PATH TRAVERSAL PROTECTION:
 *   All uploaded files are stored with UUID-based names by FileStorageService,
 *   so traversal is already structurally prevented at write time. This controller
 *   adds a second layer of defence at read time: it rejects any filename containing
 *   path separators or ".." sequences, and resolves the final path relative to the
 *   uploads directory rather than accepting an absolute path from the client.
 * FRONTEND MIGRATION:
 *   Any <img src="/uploads/filename.jpg"> tags must be replaced with an authenticated
 *   fetch that includes the Authorization: Bearer <token> header, then converted to
 *   a blob URL for display. Example:
 *   const res = await fetch(`/uploads/${filename}`, {
 *       headers: { Authorization: `Bearer ${token}` }
 *   });
 *   const blob = await res.blob();
 *   const url  = URL.createObjectURL(blob);
 *   imgElement.src = url;
 */
@RestController
@RequestMapping("/uploads")
public class FileController {

    private final Path uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads");

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename,
                                              HttpServletRequest request) {
        // Path traversal guard
        // Reject anything that tries to escape the uploads directory.
        // UUID filenames from FileStorageService will never contain these characters,
        // so this guard only fires for malicious inputs.
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Resolve and validate the file path
        // normalize() collapses any residual . or .. segments after resolution.
        // The startsWith() check ensures the resolved path is still inside uploads/.
        Path filePath = uploadRoot.resolve(filename).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            // Should not be reachable given the guard above, but defence in depth.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Load the file as a Resource
        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        // Determine Content-Type
        // Probe the actual file bytes rather than trusting the filename extension,
        // so the browser can render images inline without a download prompt.
        String contentType = null;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (IOException ignored) {
            // Fall through to the default below
        }
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // Build and return the response
        // Content-Disposition: inline — lets the browser display images/PDFs directly.
        // Change to "attachment" if you want downloads instead of inline rendering.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}