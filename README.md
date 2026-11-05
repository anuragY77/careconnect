# CareConnect: Patient–Provider EHR

A web-based Electronic Health Record system for patient records, clinical documentation, order entry, medication management and a patient portal.

> Training and demonstration project. It uses synthetic data only and must not be used for real patient care.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot |
| Frontend | Angular |
| Database | PostgreSQL |
| Runtime | Docker Compose |
| Tests | JUnit 5, Testcontainers, ArchUnit |

## Run with Docker

Requirements: Docker Desktop.

```
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env
```

Open `.env` and set `SEED_ADMIN_PASSWORD` (at least 12 characters, with a letter and a digit). Then:

```
docker compose up --build
```

| What | Address |
|---|---|
| Application | http://localhost:8080 |
| Mail inbox for development (Mailpit) | http://localhost:8025 |

Stop with `docker compose down`. Add `-v` to also delete the database.

## Run the tests

Backend (Java 21 or later, Maven, Docker running):

```
cd backend
mvn clean test
```

Frontend (Node 22.22.3 or later):

```
cd frontend
npm install
npm test -- --watch=false
npm run build
```

## Roles

PATIENT, DOCTOR, NURSE, PHARMACIST, RECEPTIONIST, LAB_TECHNICIAN, ADMIN.

## Project layout

- `backend/` Spring Boot application
- `frontend/` Angular application
- `ops/` deployment helpers
