package com.cogitosum.service;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OfxStatementParser {
    private static final Pattern TRANSACTION_BLOCK =
            Pattern.compile("(?is)<STMTTRN\\b[^>]*>(.*?)(?=<STMTTRN\\b|</BANKTRANLIST\\s*>|</OFX\\s*>|\\z)");
    private static final Pattern FIELD =
            Pattern.compile("(?is)<([A-Z][A-Z0-9]*)\\b[^>]*>(.*?)(?=<[A-Z][A-Z0-9]*\\b|\\z)");

    public List<BankReconciliationService.ImportedRow> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("The OFX/QFX file is empty");
        }
        String text = decode(bytes);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        String body = text.replaceFirst("(?is)\\A\\s*(?:<\\?xml[^>]*>\\s*)?(?:OFXHEADER:[^\\r\\n]*\\s*|DATA:[^\\r\\n]*\\s*|VERSION:[^\\r\\n]*\\s*|SECURITY:[^\\r\\n]*\\s*|ENCODING:[^\\r\\n]*\\s*|CHARSET:[^\\r\\n]*\\s*|COMPRESSION:[^\\r\\n]*\\s*|OLDFILEUID:[^\\r\\n]*\\s*|NEWFILEUID:[^\\r\\n]*\\s*)+", "");
        List<BankReconciliationService.ImportedRow> parsed;
        if (body.matches("(?is).*<\\?xml\\b.*")
                || body.matches("(?is).*<(?:DTPOSTED|TRNAMT|FITID|NAME|MEMO)\\b[^>]*>[^<]*</(?:\\w+:)?(?:DTPOSTED|TRNAMT|FITID|NAME|MEMO)\\s*>.*")) {
            parsed = parseXml(body);
        } else {
            parsed = parseSgml(body);
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("The OFX/QFX file contains no bank transactions");
        }
        return List.copyOf(parsed);
    }

    private List<BankReconciliationService.ImportedRow> parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void fatalError(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }
            });
            var document = builder.parse(new InputSource(new StringReader(xml)));
            NodeList all = document.getElementsByTagNameNS("*", "STMTTRN");
            if (all.getLength() == 0) {
                all = document.getElementsByTagName("STMTTRN");
            }
            List<BankReconciliationService.ImportedRow> rows = new ArrayList<>();
            for (int index = 0; index < all.getLength(); index++) {
                Element transaction = (Element) all.item(index);
                rows.add(toRow(
                        xmlField(transaction, "DTPOSTED"),
                        xmlField(transaction, "TRNAMT"),
                        xmlField(transaction, "FITID"),
                        xmlField(transaction, "NAME"),
                        xmlField(transaction, "MEMO"),
                        index + 1));
            }
            return rows;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("The OFX/QFX XML document is malformed or unsafe", exception);
        }
    }

    private String xmlField(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(name);
        }
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private List<BankReconciliationService.ImportedRow> parseSgml(String sgml) {
        String normalized = sgml.toUpperCase(Locale.ROOT);
        if (!normalized.contains("<OFX") || !normalized.contains("</OFX>")
                || !normalized.contains("<BANKTRANLIST")) {
            throw new IllegalArgumentException("Unsupported bank statement format; upload OFX or QFX");
        }
        Matcher matcher = TRANSACTION_BLOCK.matcher(sgml);
        List<BankReconciliationService.ImportedRow> rows = new ArrayList<>();
        int row = 0;
        while (matcher.find()) {
            row++;
            var fields = new java.util.HashMap<String, String>();
            Matcher fieldMatcher = FIELD.matcher(matcher.group(1));
            while (fieldMatcher.find()) {
                fields.putIfAbsent(fieldMatcher.group(1).toUpperCase(Locale.ROOT),
                        cleanSgmlValue(fieldMatcher.group(2)));
            }
            rows.add(toRow(fields.get("DTPOSTED"), fields.get("TRNAMT"), fields.get("FITID"),
                    fields.get("NAME"), fields.get("MEMO"), row));
        }
        long transactionTags = Pattern.compile("(?i)<STMTTRN\\b").matcher(sgml).results().count();
        if (transactionTags != rows.size()) {
            throw new IllegalArgumentException("The OFX/QFX statement contains a malformed transaction block");
        }
        return rows;
    }

    private String cleanSgmlValue(String value) {
        int nextTag = value.indexOf('<');
        String normalized = (nextTag < 0 ? value : value.substring(0, nextTag)).trim();
        return normalized.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    private BankReconciliationService.ImportedRow toRow(
            String dateText, String amountText, String fitIdText, String name, String memo, int row) {
        try {
            if (dateText == null || !dateText.trim().matches("\\d{8}(?:\\d{6}(?:\\.\\d+)?(?:\\[[^]]*])?)?")) {
                throw new IllegalArgumentException("invalid date");
            }
            LocalDate date = LocalDate.parse(dateText.trim().substring(0, 8),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            BigDecimal amount = new BigDecimal(required(amountText, "amount"))
                    .setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("zero amount");
            }
            String description = combine(name, memo);
            if (description == null || description.length() > 1000) {
                throw new IllegalArgumentException("invalid payee/memo");
            }
            String fitId = fitIdText == null || fitIdText.isBlank() ? null : fitIdText.trim();
            if (fitId != null && fitId.length() > 255) {
                throw new IllegalArgumentException("FITID is too long");
            }
            String reference = fitId;
            return new BankReconciliationService.ImportedRow(date, description, amount, reference, fitId, null);
        } catch (NumberFormatException | ArithmeticException | DateTimeParseException exception) {
            throw new IllegalArgumentException("OFX transaction " + row + " contains an invalid date or amount", exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OFX transaction " + row + " has " + exception.getMessage(), exception);
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + label);
        }
        return value.trim();
    }

    private String combine(String name, String memo) {
        String first = name == null ? "" : name.trim();
        String second = memo == null ? "" : memo.trim();
        String combined = first.isEmpty() ? second : second.isEmpty() || first.equals(second)
                ? first : first + " - " + second;
        return combined.isBlank() ? null : combined;
    }

    private String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("The OFX/QFX file must be UTF-8 encoded", exception);
        }
    }
}
