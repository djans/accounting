package com.cogitosum.service;

import com.cogitosum.entity.InvoiceTemplate;
import com.cogitosum.repository.InvoiceTemplateRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class InvoiceTemplateService {

    public static final String STANDARD_TEMPLATE_ID = "standard";
    private static final int MAX_TEMPLATE_BYTES = 5 * 1024 * 1024;

    private final InvoiceTemplateRepository templateRepository;
    private final CurrentCompanyContext companyContext;

    public InvoiceTemplateService(
            InvoiceTemplateRepository templateRepository,
            CurrentCompanyContext companyContext) {
        this.templateRepository = templateRepository;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public List<TemplateOption> getCurrentCompanyOptions() {
        Long companyId = companyContext.requireCompanyId();
        List<TemplateOption> options = new ArrayList<>();
        options.add(new TemplateOption(
            STANDARD_TEMPLATE_ID, "Standard invoice", "en", "invoice-template-example_en.docx", true));
        templateRepository.findMetadataByCompanyId(companyId).stream()
            .map(template -> new TemplateOption(template.getId(), template.getName(),
                template.getLanguageTag(), template.getOriginalFilename(), false))
            .forEach(options::add);
        return List.copyOf(options);
    }

    @Transactional(readOnly = true)
    public List<TemplateOption> getManagedTemplatesForCurrentCompany() {
        requireCompanyAdministrator();
        return getCurrentCompanyOptions();
    }

    @Transactional
    public void uploadForCurrentCompany(String name, String languageTag, String filename, byte[] data) {
        requireCompanyAdministrator();
        String normalizedName = normalizeName(name);
        String normalizedLanguageTag = normalizeLanguageTag(languageTag);
        String normalizedFilename = normalizeFilename(filename);
        validateDocx(data);

        Long companyId = companyContext.requireCompanyId();
        if (templateRepository.existsByCompanyIdAndLanguageTagAndNameIgnoreCase(
                companyId, normalizedLanguageTag, normalizedName)) {
            throw new IllegalArgumentException("An invoice template with this name and language already exists");
        }

        InvoiceTemplate template = new InvoiceTemplate();
        template.setCompany(companyContext.requireCompany());
        template.setName(normalizedName);
        template.setLanguageTag(normalizedLanguageTag);
        template.setOriginalFilename(normalizedFilename);
        template.setTemplateData(data);
        try {
            templateRepository.save(template);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException(
                "An invoice template with this name and language already exists", exception);
        }
    }

    @Transactional
    public void deleteFromCurrentCompany(String templateId) {
        requireCompanyAdministrator();
        Long companyId = companyContext.requireCompanyId();
        InvoiceTemplate template = templateRepository.findByIdAndCompanyId(templateId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Invoice template not found"));
        templateRepository.delete(template);
    }

    @Transactional(readOnly = true)
    public TemplateContent getTemplateForCompany(Long companyId, String templateId) {
        if (templateId == null || STANDARD_TEMPLATE_ID.equals(templateId)) {
            throw new IllegalArgumentException("A company invoice template must be selected");
        }
        InvoiceTemplate template = templateRepository.findByIdAndCompanyId(templateId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Selected invoice template is unavailable"));
        return new TemplateContent(
                template.getTemplateData(), template.getLanguageTag(), template.getOriginalFilename());
    }

    @Transactional(readOnly = true)
    public TemplateContent getTemplateForCurrentCompany(String templateId) {
        requireCompanyAdministrator();
        return getTemplateForCompany(companyContext.requireCompanyId(), templateId);
    }

    public String suggestedTemplateId(List<TemplateOption> options, Locale locale) {
        String requestedTag = locale.toLanguageTag();
        return options.stream()
            .filter(option -> !option.standard()
                && option.languageTag().equalsIgnoreCase(requestedTag))
            .map(TemplateOption::id)
            .findFirst()
            .or(() -> options.stream()
                .filter(option -> !option.standard()
                    && language(option.languageTag()).equalsIgnoreCase(locale.getLanguage()))
                .map(TemplateOption::id)
                .findFirst())
            .orElse(STANDARD_TEMPLATE_ID);
    }

    private void requireCompanyAdministrator() {
        if (companyContext.requireCurrentRole() != com.cogitosum.entity.UserRole.ADMIN) {
            throw new AccessDeniedException("Company administrator role required");
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank() || normalized.length() > 120) {
            throw new IllegalArgumentException("Template name is required and must be at most 120 characters");
        }
        return normalized;
    }

    private String normalizeLanguageTag(String languageTag) {
        String normalized = languageTag == null ? "" : languageTag.trim();
        if (!normalized.matches("(?i)^[a-z]{2,3}(?:-[a-z0-9]{2,8})*$")) {
            throw new IllegalArgumentException("Enter a valid language tag such as en, fr, or fr-CA");
        }
        return Locale.forLanguageTag(normalized).toLanguageTag();
    }

    private String normalizeFilename(String filename) {
        String normalized = filename == null ? "" : filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || normalized.length() > 255
                || !normalized.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("Upload a Word .docx template");
        }
        return normalized;
    }

    private void validateDocx(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_TEMPLATE_BYTES) {
            throw new IllegalArgumentException("Word templates must be non-empty and no larger than 5 MiB");
        }
        try (XWPFDocument ignored = new XWPFDocument(new ByteArrayInputStream(data))) {
            if (ignored.getParagraphs().isEmpty() && ignored.getTables().isEmpty()
                    && ignored.getHeaderList().isEmpty() && ignored.getFooterList().isEmpty()) {
                throw new IllegalArgumentException("The Word template is empty");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The uploaded file is not a valid Word .docx template", exception);
        }
    }

    private String language(String languageTag) {
        return Locale.forLanguageTag(languageTag).getLanguage();
    }

    public record TemplateOption(
            String id,
            String name,
            String languageTag,
            String originalFilename,
            boolean standard) {
    }

    public record TemplateContent(byte[] data, String languageTag, String originalFilename) {
        public TemplateContent {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }
}
