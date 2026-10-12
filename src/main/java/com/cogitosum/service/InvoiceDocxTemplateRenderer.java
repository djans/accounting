package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.LineItem;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InvoiceDocxTemplateRenderer {

    private static final String TEMPLATE_PATH = "invoice-templates/invoice-template-example_en.docx";
    private static final String ITEMS_START = "{{#lineItems}}";
    private static final String ITEMS_END = "{{/lineItems}}";
    private static final Pattern UNRESOLVED_TOKEN = Pattern.compile("\\{\\{[^{}]+}}");

    private final Resource templateResource;

    public InvoiceDocxTemplateRenderer() {
        this(new ClassPathResource(TEMPLATE_PATH));
    }

    InvoiceDocxTemplateRenderer(Resource templateResource) {
        this.templateResource = templateResource;
    }

    public byte[] render(Invoice invoice, Locale locale) {
        try (InputStream template = templateResource.getInputStream()) {
            return render(invoice, locale, template.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render the invoice Word template", exception);
        }
    }

    public byte[] render(Invoice invoice, Locale locale, byte[] templateData) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateData));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Map<String, String> values = invoiceValues(invoice, locale);
            Company company = invoice.getCompany();
            byte[] logoData = company == null ? null : company.getLogoData();
            String logoContentType = company == null ? null : company.getLogoContentType();
            replaceInParagraphs(document.getParagraphs(), values, logoData, logoContentType);
            renderTables(document.getTables(), values, invoice.getLineItems(), locale, logoData, logoContentType);
            for (var header : document.getHeaderList()) {
                replaceInParagraphs(header.getParagraphs(), values, logoData, logoContentType);
                renderTables(header.getTables(), values, invoice.getLineItems(), locale, logoData, logoContentType);
            }
            for (var footer : document.getFooterList()) {
                replaceInParagraphs(footer.getParagraphs(), values, logoData, logoContentType);
                renderTables(footer.getTables(), values, invoice.getLineItems(), locale, logoData, logoContentType);
            }
            rejectUnresolvedTokens(document);
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render the invoice Word template", exception);
        }
    }

    private Map<String, String> invoiceValues(Invoice invoice, Locale locale) {
        Company company = invoice.getCompany();
        var customer = invoice.getCustomer();
        String currencyCode = company == null || company.getCurrency() == null
                || company.getCurrency().isBlank()
                ? "CAD" : company.getCurrency();

        Map<String, String> values = new HashMap<>();
        values.put("_currencyCode", currencyCode);
        values.put("{{company.name}}", company == null ? "" : text(
                company.getLegalName() == null || company.getLegalName().isBlank()
                        ? company.getName() : company.getLegalName()));
        values.put("{{company.logo}}", "");
        values.put("{{company.address_1}}", company == null ? "" : text(company.getAddress()));
        values.put("{{company.address_2}}", company == null ? "" : addressLine2(
                company.getPostalCode(), company.getCity(), company.getProvince(), company.getCountry()));
        values.put("{{company.address}}", company == null ? "" : address(company.getAddress(),
                company.getCity(), company.getProvince(), company.getPostalCode(), company.getCountry()));
        values.put("{{company.email}}", company == null ? "" : text(company.getEmail()));
        values.put("{{company.phone}}", company == null ? "" : text(company.getPhone()));
        values.put("{{company.website}}", "");
        values.put("{{customer.name}}", customer == null ? "" : text(customer.getBusinessName()));
        values.put("{{customer.address_1}}", customer == null ? "" : text(customer.getAddress()));
        values.put("{{customer.address_2}}", customer == null ? "" : addressLine2(
                customer.getPostalCode(), customer.getCity(), customer.getProvince(), customer.getCountry()));
        values.put("{{customer.address}}", customer == null ? "" : address(customer.getAddress(),
                customer.getCity(), customer.getProvince(), customer.getPostalCode(), customer.getCountry()));
        values.put("{{customer.email}}", customer == null ? "" : text(customer.getEmail()));
        values.put("{{invoice.number}}", text(invoice.getInvoiceNumber()));
        values.put("{{invoice.date}}", formatDate(invoice.getInvoiceDate(), locale));
        values.put("{{invoice.dueDate}}", formatDate(invoice.getDueDate(), locale));
        values.put("{{invoice.subtotal}}", currency(invoice.getSubtotal(), currencyCode, locale));
        values.put("{{invoice.gstAmount}}", currency(invoice.getGstAmount(), currencyCode, locale));
        values.put("{{invoice.qstAmount}}", currency(invoice.getQstAmount(), currencyCode, locale));
        values.put("{{invoice.hstAmount}}", currency(invoice.getHstAmount(), currencyCode, locale));
        values.put("{{invoice.total}}", currency(invoice.getTotalAmount(), currencyCode, locale));
        values.put("{{invoice.notes}}", text(invoice.getNotes()));
        return values;
    }

    private void renderTables(List<XWPFTable> tables, Map<String, String> values, List<LineItem> lineItems,
                              Locale locale, byte[] logoData, String logoContentType) throws IOException {
        for (XWPFTable table : tables) {
            expandLineItemsRow(table, values, lineItems, locale, logoData, logoContentType);
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    replaceInParagraphs(cell.getParagraphs(), values, logoData, logoContentType);
                    renderTables(cell.getTables(), values, lineItems, locale, logoData, logoContentType);
                }
            }
        }
    }

    private void expandLineItemsRow(XWPFTable table, Map<String, String> values, List<LineItem> lineItems,
                                    Locale locale, byte[] logoData, String logoContentType) throws IOException {
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            String rowText = row.getTableCells().stream()
                    .flatMap(cell -> cell.getParagraphs().stream())
                    .map(XWPFParagraph::getText)
                    .reduce("", String::concat);
            boolean hasStart = rowText.contains(ITEMS_START);
            boolean hasEnd = rowText.contains(ITEMS_END);
            if (!hasStart && !hasEnd) {
                continue;
            }
            if (!hasStart || !hasEnd) {
                throw new IllegalStateException("Invoice template line-item row is missing a repeat marker");
            }

            CTRow templateRow = (CTRow) row.getCtRow().copy();
            table.removeRow(rowIndex);
            int insertionIndex = rowIndex;
            for (LineItem item : lineItems == null ? List.<LineItem>of() : lineItems) {
                XWPFTableRow renderedRow = new XWPFTableRow((CTRow) templateRow.copy(), table);
                Map<String, String> itemValues = new HashMap<>(values);
                itemValues.put("{{lineItem.description}}", text(item.getDescription()));
                itemValues.put("{{lineItem.quantity}}", decimal(item.getQuantity(), locale));
                itemValues.put("{{lineItem.unitPrice}}", currency(item.getUnitPrice(),
                        values.get("_currencyCode"), locale));
                itemValues.put("{{lineItem.total}}", currency(
                        item.getTotal() == null ? item.calculateTotal() : item.getTotal(),
                        values.get("_currencyCode"), locale));
                for (XWPFTableCell cell : renderedRow.getTableCells()) {
                    replaceInParagraphs(cell.getParagraphs(), itemValues, logoData, logoContentType);
                }
                table.addRow(renderedRow, insertionIndex++);
            }
            return;
        }
    }

    private void replaceInParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> values,
                                     byte[] logoData, String logoContentType) throws IOException {
        for (XWPFParagraph paragraph : paragraphs) {
            insertCompanyLogo(paragraph, logoData, logoContentType);
            replaceParagraphTokens(paragraph, values);
        }
    }

    private void replaceParagraphTokens(XWPFParagraph paragraph, Map<String, String> values) {
        List<CTText> textNodes = paragraph.getRuns().stream()
                .flatMap(run -> run.getCTR().getTList().stream())
                .toList();
        List<String> originalTexts = textNodes.stream().map(CTText::getStringValue).toList();
        String paragraphText = String.join("", originalTexts);
        List<StringBuilder> renderedTexts = textNodes.stream()
                .map(ignored -> new StringBuilder())
                .toList();

        Matcher matcher = UNRESOLVED_TOKEN.matcher(paragraphText);
        int cursor = 0;
        while (matcher.find()) {
            appendTextRange(originalTexts, renderedTexts, cursor, matcher.start());
            String token = matcher.group();
            String replacement = ITEMS_START.equals(token) || ITEMS_END.equals(token)
                    ? "" : values.get(token);
            if (replacement == null) {
                appendTextRange(originalTexts, renderedTexts, matcher.start(), matcher.end());
            } else {
                int nodeIndex = textNodeIndexAt(originalTexts, matcher.start());
                renderedTexts.get(nodeIndex).append(replacement);
            }
            cursor = matcher.end();
        }
        appendTextRange(originalTexts, renderedTexts, cursor, paragraphText.length());
        for (int index = 0; index < textNodes.size(); index++) {
            textNodes.get(index).setStringValue(renderedTexts.get(index).toString());
        }
    }

    private void appendTextRange(List<String> originalTexts, List<StringBuilder> renderedTexts,
                                 int from, int to) {
        int offset = 0;
        for (int index = 0; index < originalTexts.size() && offset < to; index++) {
            String text = originalTexts.get(index);
            int start = Math.max(0, from - offset);
            int end = Math.min(text.length(), to - offset);
            if (end > start) {
                renderedTexts.get(index).append(text, start, end);
            }
            offset += text.length();
        }
    }

    private int textNodeIndexAt(List<String> originalTexts, int offset) {
        int nodeOffset = 0;
        for (int index = 0; index < originalTexts.size(); index++) {
            int nodeEnd = nodeOffset + originalTexts.get(index).length();
            if (offset < nodeEnd) {
                return index;
            }
            nodeOffset = nodeEnd;
        }
        throw new IllegalStateException("Invoice template token has no text run");
    }

    private void insertCompanyLogo(XWPFParagraph paragraph, byte[] logoData, String logoContentType)
            throws IOException {
        String token = "{{company.logo}}";
        while (true) {
            List<XWPFRun> runs = paragraph.getRuns();
            List<String> runTexts = runs.stream()
                    .map(run -> run.getCTR().getTList().stream()
                            .map(CTText::getStringValue)
                            .reduce("", String::concat))
                    .toList();
            String paragraphText = String.join("", runTexts);
            int tokenStart = paragraphText.indexOf(token);
            if (tokenStart < 0) {
                return;
            }

            int tokenEnd = tokenStart + token.length();
            int offset = 0;
            int startRunIndex = -1;
            int endRunIndex = -1;
            int startRunOffset = 0;
            int endRunOffset = 0;
            for (int runIndex = 0; runIndex < runTexts.size(); runIndex++) {
                int runEnd = offset + runTexts.get(runIndex).length();
                if (startRunIndex < 0 && tokenStart >= offset && tokenStart < runEnd) {
                    startRunIndex = runIndex;
                    startRunOffset = offset;
                }
                if (tokenEnd - 1 >= offset && tokenEnd - 1 < runEnd) {
                    endRunIndex = runIndex;
                    endRunOffset = offset;
                }
                offset = runEnd;
            }
            if (startRunIndex < 0 || endRunIndex < startRunIndex) {
                throw new IllegalStateException("Could not locate {{company.logo}} in the Word paragraph");
            }

            XWPFRun startRun = runs.get(startRunIndex);
            XWPFRun endRun = runs.get(endRunIndex);
            String prefix = runTexts.get(startRunIndex).substring(0, tokenStart - startRunOffset);
            String suffix = runTexts.get(endRunIndex).substring(tokenEnd - endRunOffset);
            setRunText(startRun, prefix);
            for (int runIndex = startRunIndex + 1; runIndex < endRunIndex; runIndex++) {
                setRunText(runs.get(runIndex), "");
            }
            if (logoData != null) {
                XWPFRun imageRun = paragraph.insertNewRun(startRunIndex + 1);
                copyRunStyle(startRun, imageRun);
                addCompanyLogo(imageRun, logoData, logoContentType);
            }
            if (startRunIndex == endRunIndex && !suffix.isEmpty()) {
                int suffixIndex = startRunIndex + (logoData == null ? 1 : 2);
                XWPFRun suffixRun = paragraph.insertNewRun(suffixIndex);
                copyRunStyle(startRun, suffixRun);
                suffixRun.setText(suffix);
            } else if (startRunIndex != endRunIndex) {
                setRunText(endRun, suffix);
            }
        }
    }

    private void setRunText(XWPFRun run, String value) {
        List<CTText> textNodes = run.getCTR().getTList();
        if (textNodes.isEmpty()) {
            if (!value.isEmpty()) {
                run.setText(value);
            }
            return;
        }
        textNodes.getFirst().setStringValue(value);
        for (int index = 1; index < textNodes.size(); index++) {
            textNodes.get(index).setStringValue("");
        }
    }

    private void copyRunStyle(XWPFRun source, XWPFRun destination) {
        if (source.getCTR().getRPr() != null) {
            destination.getCTR().setRPr((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr)
                    source.getCTR().getRPr().copy());
        }
    }

    private void addCompanyLogo(XWPFRun run, byte[] logoData, String logoContentType) throws IOException {
        int pictureType = switch (logoContentType == null ? "" : logoContentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> XWPFDocument.PICTURE_TYPE_PNG;
            case "image/jpeg" -> XWPFDocument.PICTURE_TYPE_JPEG;
            default -> throw new IllegalStateException("Company logo has an unsupported image type");
        };
        String imageName = pictureType == XWPFDocument.PICTURE_TYPE_PNG
                ? "company-logo.png" : "company-logo.jpg";
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(logoData));
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            throw new IllegalStateException("Company logo is not a valid image");
        }
        double scale = Math.min(2.0 / image.getWidth(), 0.7 / image.getHeight());
        try {
            run.addPicture(new ByteArrayInputStream(logoData), pictureType, imageName,
                    Units.toEMU(image.getWidth() * scale), Units.toEMU(image.getHeight() * scale));
        } catch (InvalidFormatException exception) {
            throw new IllegalStateException("Could not add the company logo to the invoice template", exception);
        }
    }

    private void rejectUnresolvedTokens(XWPFDocument document) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.addAll(document.getParagraphs().stream().map(XWPFParagraph::getText).toList());
        collectTableText(document.getTables(), paragraphs);
        document.getHeaderList().forEach(header -> {
            paragraphs.addAll(header.getParagraphs().stream().map(XWPFParagraph::getText).toList());
            collectTableText(header.getTables(), paragraphs);
        });
        document.getFooterList().forEach(footer -> {
            paragraphs.addAll(footer.getParagraphs().stream().map(XWPFParagraph::getText).toList());
            collectTableText(footer.getTables(), paragraphs);
        });
        List<String> unresolved = paragraphs.stream()
                .flatMap(value -> UNRESOLVED_TOKEN.matcher(value).results().map(java.util.regex.MatchResult::group))
                .distinct()
                .toList();
        if (!unresolved.isEmpty()) {
            throw new IllegalStateException(
                    "Invoice template contains unknown or unexpanded placeholders: " + unresolved);
        }
    }

    private void collectTableText(List<XWPFTable> tables, List<String> paragraphs) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    paragraphs.addAll(cell.getParagraphs().stream().map(XWPFParagraph::getText).toList());
                    collectTableText(cell.getTables(), paragraphs);
                }
            }
        }
    }

    private String address(String street, String city, String province, String postalCode, String country) {
        return java.util.stream.Stream.of(street, city, province, postalCode, country)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String addressLine2(String postalCode, String city, String province, String country) {
        return java.util.stream.Stream.of(postalCode, city, province, country)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String formatDate(LocalDate value, Locale locale) {
        return value == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale).format(value);
    }

    private String currency(BigDecimal value, String currencyCode, Locale locale) {
        if (value == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(java.util.Currency.getInstance(currencyCode));
        return format.format(value);
    }

    private String decimal(BigDecimal value, Locale locale) {
        if (value == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(Math.max(0, value.stripTrailingZeros().scale()));
        return format.format(value);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
