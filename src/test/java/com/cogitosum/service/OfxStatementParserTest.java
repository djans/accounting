package com.cogitosum.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OfxStatementParserTest {
    private final OfxStatementParser parser = new OfxStatementParser();

    @Test
    void parsesOfxOneSgmlAndCombinesPayeeAndMemo() {
        String statement = "OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\n\n"
                + "<OFX><BANKMSGSRSV1><STMTRS><BANKTRANLIST>"
                + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261009120000[-4:EDT]"
                + "<TRNAMT>-19.25<FITID>FIT-77<NAME>COFFEE SHOP<MEMO>TEAM MEETING"
                + "</STMTTRN></BANKTRANLIST></STMTRS></BANKMSGSRSV1></OFX>";

        var rows = parser.parse(statement.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, rows.size());
        assertEquals("2026-10-09", rows.getFirst().transactionDate().toString());
        assertEquals("COFFEE SHOP - TEAM MEETING", rows.getFirst().description());
        assertEquals(new BigDecimal("-19.25"), rows.getFirst().amount());
        assertEquals("FIT-77", rows.getFirst().fitId());
        assertEquals("FIT-77", rows.getFirst().reference());
    }

    @Test
    void parsesOfxTwoXml() {
        String statement = """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                  <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20261010</DTPOSTED>
                    <TRNAMT>105.00</TRNAMT><FITID>deposit-1</FITID><NAME>Customer deposit</NAME>
                  </STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
                """;

        var rows = parser.parse(statement.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("105.00"), rows.getFirst().amount());
        assertEquals("Customer deposit", rows.getFirst().description());
        assertEquals("deposit-1", rows.getFirst().fitId());
    }

    @Test
    void rejectsMalformedXmlAndExternalEntities() {
        String malformed = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20260101</DTPOSTED></OFX>";
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(malformed.getBytes(StandardCharsets.UTF_8)));

        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE OFX [<!ENTITY external SYSTEM "file:///etc/passwd">]>
                <OFX><STMTTRN><DTPOSTED>20260101</DTPOSTED><TRNAMT>1</TRNAMT>
                <NAME>&external;</NAME></STMTTRN></OFX>
                """;
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(xxe.getBytes(StandardCharsets.UTF_8)));
    }
}
