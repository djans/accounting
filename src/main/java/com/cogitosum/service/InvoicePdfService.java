package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.LineItem;
import com.cogitosum.repository.InvoiceRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoicePdfService {

    private static final float LEFT = 54;
    private static final float TOP = 740;
    private static final float BOTTOM = 54;
    private static final float BODY_SIZE = 10;
    private static final float LINE_HEIGHT = 15;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final InvoiceRepository invoiceRepository;
    private final CurrentCompanyContext companyContext;

    public InvoicePdfService(InvoiceRepository invoiceRepository, CurrentCompanyContext companyContext) {
        this.invoiceRepository = invoiceRepository;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public Optional<InvoicePdf> downloadInvoice(Long invoiceId) {
        return invoiceRepository.findByIdAndCompanyId(invoiceId, companyContext.requireCompanyId())
                .map(invoice -> new InvoicePdf(createInvoicePdf(invoice), filenameFor(invoice)));
    }

    /**
     * Renders text directly with PDFBox rather than interpreting HTML. User values
     * are reduced to printable text before PDF encoding to avoid control sequences.
     */
    public byte[] createInvoicePdf(Invoice invoice) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            InvoicePdfLayout layout = new InvoicePdfLayout(document);
            layout.line("INVOICE", true);
            if (invoice.getCompany() != null) {
                layout.line(safeText(invoice.getCompany().getLegalName()), false);
                layout.line(safeText(invoice.getCompany().getEmail()), false);
            }
            layout.space();
            layout.labelValue("Invoice number", invoice.getInvoiceNumber());
            layout.labelValue("Issued", formatDate(invoice.getInvoiceDate()));
            layout.labelValue("Due", formatDate(invoice.getDueDate()));
            layout.space();
            layout.line("Bill to", true);
            if (invoice.getCustomer() != null) {
                layout.line(safeText(invoice.getCustomer().getBusinessName()), false);
                layout.line(safeText(invoice.getCustomer().getName()), false);
                layout.line(safeText(invoice.getCustomer().getEmail()), false);
                layout.line(safeText(invoice.getCustomer().getAddress()), false);
                layout.line(safeText(invoice.getCustomer().getCity()) + ", "
                        + safeText(invoice.getCustomer().getProvince()) + " "
                        + safeText(invoice.getCustomer().getPostalCode()), false);
            }
            layout.space();
            layout.line("Description                                      Qty       Unit price       Total", true);
            layout.rule();
            for (LineItem item : safeItems(invoice.getLineItems())) {
                layout.line(safeText(item.getDescription()), false);
                layout.line(String.format(Locale.ROOT, "  %-38s %8s %14s %12s",
                        "", safeDecimal(item.getQuantity()), currency(item.getUnitPrice()),
                        currency(item.getTotal() != null ? item.getTotal() : item.calculateTotal())), false);
            }
            layout.rule();
            layout.labelValue("Subtotal", currency(invoice.getSubtotal()));
            layout.labelValue("GST", currency(invoice.getGstAmount()));
            layout.labelValue("QST", currency(invoice.getQstAmount()));
            layout.labelValue("HST", currency(invoice.getHstAmount()));
            layout.labelValue("Total", currency(invoice.getTotalAmount()));
            if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                layout.space();
                layout.line("Notes", true);
                layout.line(invoice.getNotes(), false);
            }
            layout.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate the invoice PDF", e);
        }
    }

    public static String filenameFor(Invoice invoice) {
        String number = invoice == null ? null : invoice.getInvoiceNumber();
        String normalized = safeText(number).replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("[-.]{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (normalized.isBlank()) {
            normalized = "invoice";
        }
        return "invoice-" + normalized.substring(0, Math.min(normalized.length(), 80)) + ".pdf";
    }

    private static List<LineItem> safeItems(List<LineItem> lineItems) {
        return lineItems == null ? List.of() : lineItems;
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "" : DATE_FORMAT.format(date);
    }

    private static String safeDecimal(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }

    private static String currency(BigDecimal amount) {
        return amount == null ? "$0.00" : String.format(Locale.ROOT, "$%,.2f", amount);
    }

    static String safeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        StringBuilder safe = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            safe.append(character >= 32 && character <= 126 ? character : ' ');
        }
        return safe.toString().replaceAll("\\s+", " ").trim();
    }

    public record InvoicePdf(byte[] content, String filename) {
    }

    private static final class InvoicePdfLayout {
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private InvoicePdfLayout(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void line(String text, boolean emphasized) throws IOException {
            PDType1Font font = emphasized ? bold : regular;
            for (String wrapped : wrap(safeText(text), font, emphasized ? 13 : BODY_SIZE)) {
                if (y < BOTTOM) {
                    newPage();
                }
                stream.beginText();
                stream.setFont(font, emphasized ? 13 : BODY_SIZE);
                stream.newLineAtOffset(LEFT, y);
                stream.showText(wrapped);
                stream.endText();
                y -= LINE_HEIGHT;
            }
        }

        private void labelValue(String label, String value) throws IOException {
            line(label + ": " + safeText(value), false);
        }

        private void rule() throws IOException {
            if (y < BOTTOM) {
                newPage();
            }
            stream.moveTo(LEFT, y + 5);
            stream.lineTo(PDRectangle.LETTER.getWidth() - LEFT, y + 5);
            stream.stroke();
            y -= 6;
        }

        private void space() {
            y -= 6;
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = TOP;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private List<String> wrap(String text, PDType1Font font, float size) throws IOException {
            if (text.isBlank()) {
                return List.of("");
            }
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * size
                        > PDRectangle.LETTER.getWidth() - LEFT * 2) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(word);
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }
    }
}
