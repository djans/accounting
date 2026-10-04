package com.cogitosum.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseQueryServiceTest {

    private JdbcDataSource dataSource;
    private DatabaseQueryService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:database-query;DB_CLOSE_DELAY=-1");
        service = new DatabaseQueryService(dataSource);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE invoices (id INT PRIMARY KEY, amount DECIMAL(10, 2))");
            statement.execute("INSERT INTO invoices (id, amount) VALUES (1, 25.00)");
        }
    }

    @Test
    void listsOnlyTablesInTheApplicationSchema() throws Exception {
        assertTrue(service.getApplicationTables().contains("INVOICES"));
        assertFalse(service.getApplicationTables().contains("TABLES"));
    }

    @Test
    void selectReturnsBoundedTabularResults() throws Exception {
        DatabaseQueryService.QueryResult result =
                service.execute("INVOICES", "SELECT * FROM INVOICES", false);

        assertTrue(result.resultSet());
        assertEquals(1, result.rows().size());
        assertEquals("25.00", result.rows().get(0).get(1));
    }

    @Test
    void writeStatementsRequireConfirmationBeforeExecution() throws Exception {
        assertThrows(DatabaseQueryService.ConfirmationRequiredException.class,
                () -> service.execute("INVOICES",
                        "UPDATE INVOICES SET amount = 30.00 WHERE id = 1", false));

        DatabaseQueryService.QueryResult result =
                service.execute("INVOICES", "UPDATE INVOICES SET amount = 30.00 WHERE id = 1", true);

        assertEquals(1, result.affectedRows());
    }

    @Test
    void rejectsMultipleStatementsBeforeExecutingEither() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("INVOICES",
                        "UPDATE INVOICES SET amount = 30.00 WHERE id = 1; DROP TABLE INVOICES", true));
        assertEquals(1, service.execute("INVOICES", "SELECT * FROM INVOICES", false).rows().size());
    }

    @Test
    void recognizesCommentsAndUnknownCommandsAsNeedingConfirmation() {
        assertTrue(service.requiresConfirmation("/* admin */ UPDATE invoices SET amount = 0"));
        assertTrue(service.requiresConfirmation("WITH selected AS (SELECT 1) SELECT * FROM selected"));
        assertTrue(service.requiresConfirmation("SELECT * FROM invoices FOR UPDATE"));
        assertTrue(service.requiresConfirmation("SELECT * INTO OUTFILE '/tmp/result' FROM invoices"));
        assertFalse(service.requiresConfirmation("SELECT * FROM invoices"));
        assertEquals("UPDATE", service.statementType("-- update invoice\nUPDATE invoices SET amount = 0"));
    }
}
