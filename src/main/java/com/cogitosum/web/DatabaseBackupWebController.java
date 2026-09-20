package com.cogitosum.web;

import com.cogitosum.service.DatabaseBackupService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/database")
public class DatabaseBackupWebController {

    private final DatabaseBackupService backupService;

    public DatabaseBackupWebController(DatabaseBackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/backup")
    public String page(Model model) {
        requireAdmin();
        return "database/backup";
    }

    @GetMapping("/backup/export")
    public ResponseEntity<ByteArrayResource> export() throws Exception {
        requireAdmin();
        byte[] content = backupService.exportDatabase();
        String filename = "accounting-backup-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString())
            .contentLength(content.length)
            .body(new ByteArrayResource(content));
    }

    @PostMapping("/backup/import")
    public String importBackup(@RequestParam("file") MultipartFile file, RedirectAttributes attributes) {
        requireAdmin();
        try {
            if (file.isEmpty()) throw new IllegalArgumentException("Please select a backup file");
            int rows = backupService.importDatabase(file.getBytes());
            attributes.addFlashAttribute("flashSuccess", rows + " database rows restored successfully.");
        } catch (Exception e) {
            attributes.addFlashAttribute("flashError", "Could not restore backup: " + e.getMessage());
        }
        return "redirect:/database/backup";
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Administrator role required");
        }
    }
}
