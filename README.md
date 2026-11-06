# Banking Management System

A Spring Boot REST API with a web frontend for managing bank accounts, transactions, balances and history.

- **Backend:** Java 17, Spring Boot 3.5, Spring Data JPA (Hibernate), Bean Validation
- **Database:** H2 file database by default (no setup), MySQL through a profile
- **Frontend:** plain HTML, CSS and JavaScript, served by Spring Boot from the same app

## Run it

You need JDK 17 or newer and Maven.

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. Data is stored in `./data` and survives restarts. The H2 console is at
<http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/bankdb`, user `sa`, empty password).

### Use MySQL instead

```bash
export DB_USERNAME=root
export DB_PASSWORD=your_password
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

The app creates the `bankdb` database and tables on first start. `database/schema-mysql.sql` shows the same
schema if you want to create it by hand.

### Run the tests

```bash
mvn test
```

## What it does

| Feature | Details |
| --- | --- |
| Accounts | Open Savings or Current accounts with an optional opening deposit, list them, view one, close one |
| Transactions | Deposit, withdraw, and transfer between accounts |
| Balances | Live balance per account, plus the running balance after every transaction |
| History | Passbook-style ledger (debit, credit, balance), newest first, filterable by type |

## Transaction rules

- The amount must be greater than zero and have at most 2 decimal places.
- A single transaction cannot exceed `bank.max-transaction-amount` (1,000,000 by default, set in `application.properties`).
- A withdrawal or transfer cannot take an account below zero.
- A transfer needs two different, existing accounts.
- Closed accounts cannot send or receive money. An account can only be closed when its balance is exactly zero, and its history is kept.
- Every money movement runs in one database transaction with row locks, so a failure rolls everything back and two requests at the same time cannot corrupt a balance. Transfers lock accounts in a fixed order to avoid deadlocks.

## API

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| POST | `/api/accounts` | Open an account | 201 |
| GET | `/api/accounts` | List accounts | 200 |
| GET | `/api/accounts/{accountNumber}` | Get one account | 200 |
| GET | `/api/accounts/{accountNumber}/balance` | Get the balance | 200 |
| DELETE | `/api/accounts/{accountNumber}` | Close an account | 200 |
| POST | `/api/accounts/{accountNumber}/deposit` | Deposit money | 200 |
| POST | `/api/accounts/{accountNumber}/withdraw` | Withdraw money | 200 |
| POST | `/api/transfers` | Transfer between accounts | 200 |
| GET | `/api/accounts/{accountNumber}/transactions?type=&limit=` | Transaction history | 200 |

Examples:

```bash
curl -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"holderName":"Asha Rao","email":"asha@example.com","accountType":"SAVINGS","initialDeposit":5000}'

curl -X POST localhost:8080/api/accounts/1234567890/deposit -H 'Content-Type: application/json' \
  -d '{"amount":250.50,"description":"Salary"}'

curl -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' \
  -d '{"fromAccount":"1234567890","toAccount":"0987654321","amount":100}'
```

### Errors

Every error has the same shape, so clients can rely on `code`:

```json
{
  "timestamp": "2026-10-30T10:31:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "INSUFFICIENT_FUNDS",
  "message": "Account 1234567890 has 300.00 available, which is less than the requested 300.01",
  "path": "/api/accounts/1234567890/withdraw"
}
```

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | A field is missing or invalid (the response lists them in `fieldErrors`) |
| 400 | `MALFORMED_REQUEST` | The body is not valid JSON, or a value has the wrong type |
| 400 | `INVALID_TRANSACTION` | A rule is broken, such as an amount over the limit or a transfer to the same account |
| 404 | `ACCOUNT_NOT_FOUND` | The account number does not exist |
| 409 | `ACCOUNT_STATE_CONFLICT` | The account is closed, or still has a balance when closing |
| 422 | `INSUFFICIENT_FUNDS` | The balance is too low for the withdrawal or transfer |
| 500 | `INTERNAL_ERROR` | Unexpected server error (details go to the server log only) |

## Tests

`BankingApiIntegrationTest` calls the real API with valid and invalid requests against an in-memory database. It covers
validation errors, malformed JSON, the transaction rules above, rollback on failed transfers, history order and
filtering, and closing accounts.

## Project layout

```
src/main/java/com/vignesh/banking
  controller/   REST endpoints
  service/      business logic and transaction rules
  repository/   Spring Data JPA repositories
  model/        JPA entities and enums
  dto/          request and response records
  exception/    business exceptions and the global error handler
src/main/resources
  application.properties         default (H2) configuration
  application-mysql.properties   MySQL profile
  static/                        frontend (index.html, css, js)
database/schema-mysql.sql        reference schema
```
