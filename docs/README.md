# Canadian Billing System - Getting Started

## Quick Start

### Prerequisites
- Java 25 or higher
- Maven 3.6 or higher
- Docker (optional)

### Clone and Build
```bash
# Navigate to project directory
cd accounting

# Build the project
./build.sh          # On Linux/Mac
build.bat          # On Windows

# Or manually:
mvn clean install
```

### Run Locally
```bash
# Development with auto-reload
mvn spring-boot:run

# Application will be available at:
http://localhost:8080
```

### Run with Docker
```bash
# Build Docker image
docker build -t accounting:1.0 .

# Run container
docker run -p 8080:8080 accounting:1.0

# Or with Docker Compose
docker-compose up
```

## First Steps - Create Sample Data

### 1. Create a Customer
```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Smith",
    "email": "john@example.com",
    "businessName": "Smith Consulting Inc.",
    "address": "123 Main Street",
    "city": "Toronto",
    "province": "ON",
    "postalCode": "M5H 2N2",
    "country": "Canada",
    "businessNumber": "123456789",
    "gstNumber": "123456789RT0001"
  }'
```

### 2. Get the Customer ID from response, then Create an Invoice
```bash
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "lineItems": [
      {
        "description": "Consulting Services - 10 hours",
        "quantity": 10,
        "unitPrice": 150.00
      },
      {
        "description": "Software License",
        "quantity": 1,
        "unitPrice": 500.00
      }
    ],
    "notes": "Thank you for your business!"
  }'
```

### 3. Record a Payment
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": 1,
    "amount": 1500.00,
    "paymentMethod": "BANK_TRANSFER",
    "transactionId": "TXN-001"
  }'
```

### 4. View Reports
```bash
# Revenue Report
curl "http://localhost:8080/api/reports/revenue?startDate=2024-05-01&endDate=2024-05-31"

# Aging Analysis
curl "http://localhost:8080/api/reports/aging"

# Invoice Status Summary
curl "http://localhost:8080/api/reports/invoice-status-summary"

# Tax Summary
curl "http://localhost:8080/api/reports/tax-summary?startDate=2024-05-01&endDate=2024-05-31"
```

## API Documentation

See `API_DOCUMENTATION.md` for complete API reference with all endpoints and examples.

## Implementation Details

See `IMPLEMENTATION_SUMMARY.md` for detailed information about what was implemented.

## Project Structure

- **entity/** - JPA entities (Customer, Invoice, LineItem, Payment)
- **repository/** - Spring Data JPA repositories
- **service/** - Business logic layer
- **controller/** - REST API endpoints
- **dto/** - Data Transfer Objects
- **exception/** - Exception handling

## Features Included

✅ Customer Management
✅ Invoice Creation & Tracking
✅ Payment Processing
✅ Automatic GST/HST Calculation (Canadian Tax System)
✅ Business Reports & Analytics
✅ Payment Status Tracking
✅ Invoice Workflow Management
✅ Docker Support
✅ JPA Database Persistence
✅ Exception Handling
✅ Integration Tests

## Database

**Default**: H2 (in-memory, development/testing only)

**To use PostgreSQL**:
1. Update `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/accounting
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

2. Create database:
```sql
CREATE DATABASE accounting;
```

## Common Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | /api/customers | Create customer |
| GET | /api/customers | List customers |
| POST | /api/invoices | Create invoice |
| GET | /api/invoices | List invoices |
| GET | /api/invoices/overdue | Get overdue invoices |
| POST | /api/payments | Record payment |
| GET | /api/reports/revenue | Revenue report |
| GET | /api/reports/aging | Aging analysis |

## Troubleshooting

### Maven not found
Install Maven from https://maven.apache.org/download.cgi

### Port 8080 already in use
Change port in `application.properties`:
```properties
server.port=8081
```

### H2 Database Console
Access at: http://localhost:8080/h2-console
- JDBC URL: jdbc:h2:mem:testdb
- User Name: sa
- Password: (leave blank)

## Support

For issues and questions, refer to `API_DOCUMENTATION.md` for detailed endpoint information.

## Next Steps

1. Add authentication/authorization
2. Implement email notifications
3. Add PDF invoice generation
4. Integrate with payment gateways (Stripe, PayPal)
5. Deploy to production environment

Happy Billing! 🎉

