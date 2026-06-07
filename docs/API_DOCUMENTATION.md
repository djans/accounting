# Canadian Billing System - Backend API

A comprehensive Spring Boot billing system designed for Canadian companies with built-in support for GST/HST tax calculations.

## Features

- **Customer Management**: Create and manage customer profiles with Canadian address and tax information
- **Invoice Management**: Create, send, and track invoices with automatic tax calculations
- **Payment Processing**: Record and track payments with multiple payment methods
- **Tax Calculation**: Automatic GST/HST calculations based on provincial rates
- **Business Reports**: Revenue, aging analysis, invoice status summaries, and tax reports
- **Docker Support**: Pre-configured Docker setup for easy deployment

## Technology Stack

- Java 25
- Spring Boot 3.2.0
- Spring Data JPA
- H2 Database (default, easily configurable to PostgreSQL, MySQL)
- Maven
- Docker

## Running the Application

### Prerequisites
- Java 25 or higher
- Maven 3.6+
- Docker (optional)

### Local Development
```bash
# Clone the repository and navigate to the project directory

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# The application will start on http://localhost:8080
```

### Docker
```bash
# Build Docker image
docker build -t accounting:1.0 .

# Run Docker container
docker run -p 8080:8080 accounting:1.0

# Or use Docker Compose
docker-compose up
```

## API Endpoints

### Customer Management

#### Create Customer
```
POST /api/customers
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "businessName": "Acme Corp",
  "address": "123 Main St",
  "city": "Toronto",
  "province": "ON",
  "postalCode": "M5H 2N2",
  "country": "Canada",
  "businessNumber": "123456789",
  "gstNumber": "123456789RT0001"
}
```

#### Get All Customers
```
GET /api/customers
```

#### Get Customer by ID
```
GET /api/customers/{id}
```

#### Get Customer by Email
```
GET /api/customers/email/{email}
```

#### Get Customer by GST Number
```
GET /api/customers/gst/{gstNumber}
```

#### Update Customer
```
PUT /api/customers/{id}
Content-Type: application/json
```

#### Delete Customer
```
DELETE /api/customers/{id}
```

### Invoice Management

#### Create Invoice
```
POST /api/invoices
Content-Type: application/json

{
  "customerId": 1,
  "invoiceDate": "2024-05-24",
  "dueDate": "2024-06-24",
  "lineItems": [
    {
      "description": "Consulting Services",
      "quantity": 10,
      "unitPrice": 150.00
    }
  ],
  "notes": "Thank you for your business"
}
```

#### Get All Invoices
```
GET /api/invoices
```

#### Get Invoice by ID
```
GET /api/invoices/{id}
```

#### Get Invoice by Invoice Number
```
GET /api/invoices/number/{invoiceNumber}
```

#### Get Invoices by Customer
```
GET /api/invoices/customer/{customerId}
```

#### Get Invoices by Status
```
GET /api/invoices/status/{status}
```

Status values: DRAFT, SENT, VIEWED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED, REFUNDED

#### Get Invoices by Date Range
```
GET /api/invoices/date-range?startDate=2024-05-01&endDate=2024-05-31
```

#### Get Overdue Invoices
```
GET /api/invoices/overdue
```

#### Mark Invoice as Sent
```
PUT /api/invoices/{id}/send
```

#### Mark Invoice as Viewed
```
PUT /api/invoices/{id}/view
```

#### Cancel Invoice
```
PUT /api/invoices/{id}/cancel
```

#### Update Invoice
```
PUT /api/invoices/{id}
Content-Type: application/json
```

#### Delete Invoice
```
DELETE /api/invoices/{id}
```

### Payment Management

#### Record Payment
```
POST /api/payments
Content-Type: application/json

{
  "invoiceId": 1,
  "amount": 1500.00,
  "paymentDate": "2024-05-24",
  "paymentMethod": "BANK_TRANSFER",
  "transactionId": "TXN-ABC123"
}
```

Payment Methods: CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CHEQUE, PAYPAL, CRYPTOCURRENCY, CASH, OTHER

#### Get All Payments
```
GET /api/payments
```

#### Get Payment by ID
```
GET /api/payments/{id}
```

#### Get Payment by Transaction ID
```
GET /api/payments/transaction/{transactionId}
```

#### Get Payments by Invoice
```
GET /api/payments/invoice/{invoiceId}
```

#### Get Payments by Status
```
GET /api/payments/status/{status}
```

Status values: PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED

#### Get Payments by Date Range
```
GET /api/payments/date-range?startDate=2024-05-01&endDate=2024-05-31
```

#### Mark Payment as Completed
```
PUT /api/payments/{id}/complete
```

#### Refund Payment
```
PUT /api/payments/{id}/refund
```

#### Delete Payment
```
DELETE /api/payments/{id}
```

### Reports & Analytics

#### Revenue Report
```
GET /api/reports/revenue?startDate=2024-05-01&endDate=2024-05-31
```

Response:
```json
{
  "totalRevenue": 15000.00,
  "totalPaid": 10000.00,
  "totalOutstanding": 5000.00,
  "invoiceCount": 5
}
```

#### Aging Analysis
```
GET /api/reports/aging
```

Response:
```json
{
  "current": 2000.00,
  "30Days": 1500.00,
  "60Days": 1000.00,
  "90DaysPlus": 500.00,
  "totalOutstanding": 5000.00
}
```

#### Invoice Status Summary
```
GET /api/reports/invoice-status-summary
```

Response:
```json
{
  "draft": 2,
  "sent": 5,
  "viewed": 3,
  "partiallyPaid": 2,
  "paid": 8,
  "overdue": 1,
  "cancelled": 0,
  "refunded": 0
}
```

#### Tax Summary
```
GET /api/reports/tax-summary?startDate=2024-05-01&endDate=2024-05-31
```

Response:
```json
{
  "totalGst": 750.00,
  "totalHst": 1950.00,
  "totalTax": 2700.00
}
```

## Database Schema

The application uses JPA/Hibernate with the following tables:

- **customers**: Stores customer information
- **invoices**: Stores invoice records
- **line_items**: Stores individual line items for each invoice
- **payments**: Stores payment transactions

## Canadian Tax Support

The system automatically calculates taxes based on the customer's province:

- **GST Provinces**: BC, AB, SK, MB (5% GST)
- **HST Provinces**: ON, NS, NB, NL, PE (13% HST)
- **QC**: Can be configured with QST

Tax rates are configurable in the `InvoiceService` class.

## Configuration

### Application Properties

Edit `src/main/resources/application.properties`:

```properties
spring.application.name=accounting
server.port=8080
spring.datasource.url=jdbc:h2:mem:testdb
spring.h2.console.enabled=true

# For PostgreSQL
# spring.datasource.url=jdbc:postgresql://localhost:5432/accounting
# spring.datasource.username=postgres
# spring.datasource.password=password
# spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

## Error Handling

The API returns standardized error responses:

```json
{
  "status": 400,
  "message": "Error description"
}
```

Common HTTP Status Codes:
- 200 OK: Successful GET request
- 201 Created: Successful POST request
- 204 No Content: Successful DELETE request
- 400 Bad Request: Invalid input data
- 404 Not Found: Resource not found
- 500 Internal Server Error: Server error

## Project Structure

```
src/main/java/com/cogitosum/
├── Main.java                 # Spring Boot application entry point
├── controller/               # REST Controllers
│   ├── CustomerController.java
│   ├── InvoiceController.java
│   ├── PaymentController.java
│   └── ReportController.java
├── entity/                   # JPA Entities
│   ├── Customer.java
│   ├── Invoice.java
│   ├── LineItem.java
│   ├── Payment.java
│   ├── InvoiceStatus.java
│   ├── PaymentMethod.java
│   └── PaymentStatus.java
├── repository/               # JPA Repositories
│   ├── CustomerRepository.java
│   ├── InvoiceRepository.java
│   └── PaymentRepository.java
├── service/                  # Business Logic
│   ├── CustomerService.java
│   ├── InvoiceService.java
│   ├── PaymentService.java
│   └── BillingReportService.java
├── dto/                      # Data Transfer Objects
│   ├── InvoiceDTO.java
│   ├── LineItemDTO.java
│   └── PaymentDTO.java
└── exception/                # Exception Handling
    ├── BillingException.java
    ├── GlobalExceptionHandler.java
    └── ErrorResponse.java
```

## Future Enhancements

- Email notification for invoices and payment reminders
- PDF invoice generation
- Multi-currency support
- Recurring invoices
- Invoice templates
- Payment gateway integration
- Advanced analytics with charts
- API authentication and authorization
- Audit logging

## License

This project is licensed under the MIT License.

## Support

For issues and questions, please create an issue in the repository.

