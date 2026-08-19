# Finance Tracker Backend

The backend service for the Finance Tracker application, providing RESTful APIs for managing personal finances. 
Built with Java 21, Spring Boot, and MySQL.

## Stack

| Layer   | Choice                                                 |
| ------- | ------------------------------------------------------ |
| API     | Spring Boot 3.4 (Java 21)                              |
| Data    | Spring Data JPA + Hibernate                            |
| DB      | MySQL 8+                                               |
| Migrations | Flyway                                            |
| Auth    | Spring Security + JWT                                  |

## Getting started

Requires Java 21+ and MySQL.

1. **Database Setup**
   Ensure MySQL is running and create a database (e.g., `finance_tracker`).
   Update `src/main/resources/application.properties` or set environment variables for database credentials.

2. **Run the Application**
   ```bash
   ./mvnw spring-boot:run
   ```

## Rules that are not negotiable

These exist because getting them wrong corrupts financial data silently.

1. **Money is never a `float` or `double`.** Use `BigDecimal` in calculations, `String` across JSON boundaries.
2. **A transfer is not an expense.** One row, two account FKs, with strict validation making a malformed transfer unrepresentable.
3. **Dates are calendar dates.** `LocalDate` and `OffsetDateTime` should be used correctly depending on the context.
4. **`userId` comes from the JWT session, never from client input.** No request schema contains a `userId` field.
5. **Soft delete everywhere.** Every financial read filters `deletedAt`.
