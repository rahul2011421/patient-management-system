# Patient Management System

Patient Management System is a **microservices-based backend** for managing patients, appointments, billing, and authentication. Built with Java 21 and Spring Boot, the system uses Apache Kafka for event-driven communication, gRPC for synchronous inter-service calls, and MySQL as the primary data store, with all services orchestrated using Docker Compose.

---

## Project Overview

The **Patient Management System** is a backend platform built on microservice architecture that handles:

- **Patient lifecycle management** — Create, read, update, and delete patient records with search and pagination.
- **Appointment scheduling** — Query appointments by date range with enriched patient data sourced via event-driven caching (CQRS pattern).
- **Billing account provisioning** — Automatically creates a billing account upon patient registration via gRPC, with a Kafka-based async fallback using circuit breakers.
- **Authentication & Authorization** — JWT-based login and token validation centralized in the Auth Service.
- **API Gateway** — Single entry point for all clients with JWT validation, rate limiting, and route management.
- **Analytics** — Consumes patient events from Kafka for downstream analytics processing.

---

## Architecture Overview

### Patient Management Architecture Diagram
(https://github.com/rahul2011421/patient-management-system/blob/main/images/Patient%20Management%20Architecture%20Diagram.png)



### High-Level Description

The system follows a **microservices** design where each service owns its own database (Database-per-Service pattern). Services communicate asynchronously via **Apache Kafka** (using Protobuf-serialized events) and synchronously via **gRPC**. All external traffic is routed through a **Spring Cloud API Gateway**.

```
Frontend Client
      │
      ▼
Application Load Balancer / API Gateway (port 4004)
      │        │
      │        └── JWT Validation Filter ──► Auth Service (port 4005)
      │
      ├──► Patient Service (port 4000)
      │         ├── MySQL (Patient DB)
      │         ├── Redis (response cache)
      │         ├── gRPC Client ──────────► Billing Service (port 4001 / gRPC 9001)
      │         └── Kafka Producer ────────► Topic: patient.created / patient.updated
      │
      ├──► Appointment Service (port 4006)
      │         ├── MySQL (Appointment DB)
      │         │     ├── appointments_table
      │         │     └── cached_patients_table
      │         └── Kafka Consumer ◄────── Topic: patient.created / patient.updated
      │
      └──► Analytics Service
                └── Kafka Consumer ◄────── Topic: patient
```

### Key Components and Their Responsibilities

| Service | Port | Responsibility |
|---|---|---|
| **API Gateway** | 4004 | Routes requests, enforces JWT auth, rate limits per IP via Redis |
| **Auth Service** | 4005 | User login, JWT generation and validation |
| **Patient Service** | 4000 | Full CRUD for patients, Redis caching, Swagger docs, Kafka producer, gRPC client |
| **Billing Service** | 4001 / 9001 | gRPC server that provisions billing accounts; async fallback via Kafka |
| **Appointment Service** | 4006 | Appointment queries by date range; maintains a local CQRS read-model of patient data |
| **Analytics Service** | 4002 | Kafka consumer that processes patient events for analytics workloads |

### Data / Request Flow

**Patient Creation Flow:**
1. Client sends `POST /api/patients` → API Gateway validates JWT → Patient Service
2. Patient Service persists the record to its own MySQL database
3. Patient Service calls Billing Service via **gRPC** to create a billing account
   - If gRPC fails → **Circuit Breaker** triggers fallback → emits a `billing-account` Kafka event
4. Patient Service publishes a `patient.created` **Protobuf** event to Kafka
5. **Appointment Service** consumes `patient.created` and upserts the patient into its local `cached_patients_table` (CQRS read-model)
6. **Analytics Service** consumes the same event for analytics processing

**Appointment Query Flow:**
1. Client sends `GET /appointments?from=...&to=...` directly to Appointment Service
2. Appointment Service queries its local `appointments_table` filtered by time range
3. For each appointment, it enriches the response with the patient's name from the local `cached_patients_table` — no cross-service call needed at query time

---

## Technologies Used

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| API Style | REST (Spring MVC) + gRPC |
| API Gateway | Spring Cloud Gateway |
| Message Broker | Apache Kafka (official `apache/kafka:latest`, KRaft mode — no Zookeeper) |
| Serialization | Protocol Buffers (Protobuf) |
| Database | MySQL 8 |
| Caching | Redis 7 |
| Resilience | Resilience4j (Circuit Breaker + Retry) |
| Containerization | Docker + Docker Compose |
| Build Tool | Apache Maven |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Observability | Spring Actuator + Prometheus + Grafana |
| Testing | JUnit 5, REST Assured (integration tests) |
| IDE | IntelliJ IDEA |

---

## Project Structure

```
patient-management/
│
├── docker-compose.yml            # Orchestrates all services, databases, Kafka, Redis
│
├── api-gateway/                  # Spring Cloud Gateway — single entry point
│   └── src/main/
│       ├── java/
│       │   ├── filter/           # JwtValidationGatewayFilterFactory (JWT auth per route)
│       │   └── config/           # RateLimiterConfig (IP-based rate limiting via Redis)
│       └── resources/
│           ├── application.yml   # Local routing rules
│           └── application-prod.yml  # Production routing (DNS-based service resolution)
│
├── auth-service/                 # Authentication — JWT login & validation
│   └── src/main/java/
│       ├── controller/           # AuthController (/login, /validate)
│       ├── Service/              # AuthService, UserService
│       ├── model/                # User entity
│       └── util/                 # JwtUtil
│
├── patient-service/              # Core patient CRUD service
│   └── src/main/java/
│       ├── controller/           # PatientController (GET, POST, PUT, DELETE)
│       ├── service/              # PatientService (business logic, caching)
│       ├── model/                # Patient entity
│       ├── dto/                  # Request/Response DTOs with validation groups
│       ├── repository/           # PatientRepository (JPA + search)
│       ├── grpc/                 # BillingServiceGrpcClient (with circuit breaker)
│       ├── kafka/                # KafkaProducer (patient.created / patient.updated)
│       ├── cache/                # RedisCacheConfig (10-minute TTL)
│       ├── aspects/              # PatientServiceMetrics (AOP-based Prometheus counters)
│       ├── mapper/               # PatientMapper
│       └── exception/            # GlobalExceptionHandler, custom exceptions
│
├── appointment-service/          # Appointment queries with CQRS read-model
│   └── src/main/java/
│       ├── controller/           # AppointmentController (GET by date range)
│       ├── service/              # AppointmentService
│       ├── entity/               # Appointment, CachedPatient
│       ├── repository/           # AppointmentRepository, CachedPatientRepository
│       ├── kafka/                # KafkaConsumer (patient.created / patient.updated)
│       ├── dto/                  # AppointmentRequestDto, AppointmentResponseDto
│       └── exception/            # GlobalExceptionHandler
│
├── billing-service/              # Billing account provisioning
│   └── src/main/java/
│       ├── grpc/                 # BillingGrpcService (gRPC server implementation)
│       └── kafka/                # KafkaConsumer (billing-account fallback events)
│
├── analytics-service/            # Event-driven analytics consumer
│   └── src/main/java/
│       └── kafka/                # KafkaConsumer (patient topic)
│
├── integration-tests/            # End-to-end REST Assured tests
│   └── src/test/java/
│       ├── PatientIntegrationTest.java   # Patient CRUD + rate limit tests
│       └── AuthIntegrationTest.java      # Login / token validation tests
│
├── monitoring/
│   ├── prometheus.yml            # Prometheus scrape config (patient-service metrics)
│   └── Dockerfile                # Prometheus container setup
│
├── api-requests/                 # Sample HTTP request files (IntelliJ HTTP Client)
│   ├── auth-service/             # login.http, Validate.http
│   ├── patient-service/          # create, get, update, delete patient requests
│   └── appointment-service/      # get-appointment-by-date-range.http
│
└── grpc-requests/
    └── billing-service/          # create-billing-account.http (gRPC test)
```

---

## Setup & Installation

### Clone the Repository

```bash
git clone https://github.com/rahul2011421/patient-management
cd patient-management
```

---

## How to Run the Project

Make sure **Docker Desktop** is open and the engine is running before proceeding.

---

### Step 1 — First-time or clean start

Build all images and start the full stack:

```powershell
docker compose up --build
```

Docker Compose starts services in dependency order:
1. MySQL databases (patient, auth, appointment) — with health checks
2. Redis
3. Kafka (official `apache/kafka:latest`, KRaft mode — no Zookeeper required)
4. All six microservices (wait for healthy dependencies before starting)

> First run takes **5–10 minutes** — Maven downloads all dependencies inside Docker for each service.

---

### Step 2 — Verify everything is running

```powershell
docker compose ps
```

---

### Step 3 — Start Prometheus & Grafana (optional)

Monitoring services are behind a Docker Compose **profile** and must be started separately:

```powershell
docker compose --profile monitoring up -d Prometheus Grafana
```

Once running:
- **Prometheus** → `http://localhost:9090`
- **Grafana** → `http://localhost:3000` (default login: `admin` / `admin`)

---

### Troubleshooting: Stale Container Conflicts

If `docker compose up` fails with a container name conflict (e.g. `The container name "/kafka" is already in use`), force-remove all containers and start clean:

```powershell
docker ps -aq | ForEach-Object { docker rm -f $_ }
docker compose up --build
```

---

### Useful Commands

| Goal | Command |
|---|---|
| Start (no rebuild) | `docker compose up` |
| Start in background | `docker compose up -d` |
| Stop all containers | `docker compose down` |
| Stop + delete volumes (fresh DB) | `docker compose down -v` |
| View logs for one service | `docker compose logs -f patient-service` |
| Restart one service | `docker compose restart appointment-service` |
| Rebuild one service only | `docker compose up --build patient-service` |

---


## Appointment Service

The Appointment Service implements the **CQRS (Command Query Responsibility Segregation)** pattern to decouple appointment queries from patient data management:

**CQRS Read-Model (Kafka Consumer)**
- Listens to `patient.created` and `patient.updated` Kafka topics using the `appointment-service` consumer group
- Deserializes Protobuf-encoded `PatientEvent` messages
- Upserts patient data (`id`, `fullName`, `email`) into a local `cached_patients` table in its own MySQL database
- This eliminates any runtime dependency on the Patient Service during appointment queries

---

## Future Scope

### CI/CD & Deployment, Kibana (logs) integration & Better Error Handling

---

## Made by

**Rahul Yadav**
