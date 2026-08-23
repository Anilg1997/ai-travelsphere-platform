# ✈️ TravelSphere — AI-Powered Travel Booking & Insurance Platform

A full-stack enterprise microservices platform for booking flights, hotels, cars, transport, food delivery, insurance packages, with AI-powered trip planning, real-time notifications, and a Google Maps-integrated mini-map.

[![CI](https://github.com/Anilg1997/TravelSphere-Nexus-AI-Powered-Travel-Booking-Insurance-Platform/actions/workflows/ci.yml/badge.svg)](https://github.com/Anilg1997/TravelSphere-Nexus-AI-Powered-Travel-Booking-Insurance-Platform/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17-orange?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-17-red?style=flat&logo=angular&logoColor=white)](https://angular.io/)
[![Docker](https://img.shields.io/badge/Docker%20Compose-blue?style=flat&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-black?style=flat&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 🚀 Quick Start

### Prerequisites

- **Java 17** and **Maven 3.9+** — [Install](https://adoptium.net/) (for running services locally)
- **Node.js 18+** and **npm** — [Install](https://nodejs.org/) (for frontend)
- **Docker Desktop** (with Docker Compose v2) — [Install](https://docs.docker.com/get-docker/) (optional — for infrastructure)

### Option A: Run Locally (Recommended for Development)

Run each service directly from your IDE or terminal — no Docker needed for backend services.

```bash
# Clone the repository
git clone https://github.com/Anilg1997/TravelSphere-Nexus-AI-Powered-Travel-Booking-Insurance-Platform.git
cd TravelSphere-Nexus-AI-Powered-Travel-Booking-Insurance-Platform

# 1. Start infrastructure (PostgreSQL, Redis, Kafka) via Docker
docker compose up -d postgres redis zookeeper kafka qdrant localstack mailhog

# 2. Run a single service (builds + starts it)
./start.sh local auth-service
# OR run from IDE: Run ServiceRegistryApplication.java, then auth-service, etc.

# 3. Start the Angular frontend
cd frontend/travelsphere-ui && npm install && ng serve
```

**Start order for local development:**
1. `service-registry` (port 8761) — Eureka must start first
2. `config-server` (port 8888) — Config server needs Eureka
3. `api-gateway` (port 8080) — Gateway needs Eureka + Config
4. Any business service (auth, user, flight, etc.) — needs Eureka + DB + Kafka

Each service is **self-contained** — it has its own `application.yml` with all config and works without the Config Server.

**Run a single service:**
```bash
# Linux/macOS
./start.sh local auth-service

# Windows
start.bat local auth-service

# Or directly with Maven
cd backend/auth-service
mvn spring-boot:run
```

**Run all services locally:**
```bash
./start.sh local-all
```

### Option B: Run Everything via Docker

```bash
# Start everything (infrastructure + all backend services via Docker)
./start.sh

# On Windows:
start.bat
```

This starts:
- **Infrastructure**: PostgreSQL, Redis, Kafka, Qdrant, LocalStack, MailHog
- **Monitoring**: Zipkin, Prometheus, Grafana, Kafka UI
- **Backend**: All 17 microservices via Eureka service discovery
- **API Gateway**: Routes all traffic through port 8080

### Start the Frontend

```bash
cd frontend/travelsphere-ui
npm install
ng serve
```

Open **http://localhost:4200** in your browser.

---

## 📋 Available Commands

| Command | Description |
|---------|-------------|
| `./start.sh` | Start infrastructure + all backend services (Docker) |
| `./start.sh infra` | Start only infrastructure (DB, Redis, Kafka, etc.) |
| `./start.sh frontend` | Start Angular dev server |
| `./start.sh backend` | Start all backend microservices (Docker) |
| `./start.sh local <svc>` | Run a single service locally (e.g. `./start.sh local auth-service`) |
| `./start.sh local-all` | Run ALL services locally (requires Java 17 + Maven) |
| `./start.sh test` | Run backend unit tests |
| `./start.sh stop` | Stop all services |
| `./start.sh status` | Show running container status |
| `./start.sh logs` | Tail logs from all services |

Windows: Replace `./start.sh` with `start.bat`.

---

## 🧪 Tests & CI Badges

[![CI Pipeline](https://github.com/Anilg1997/TravelSphere-Nexus-AI-Powered-Travel-Booking-Insurance-Platform/actions/workflows/ci.yml/badge.svg?label=Backend%20Tests)](https://github.com/Anilg1997/TravelSphere-Nexus-AI-Powered-Travel-Booking-Insurance-Platform/actions/workflows/ci.yml)
[![Auth Service Tests](https://img.shields.io/badge/Auth%20Service-Tests%20Passing-brightgreen?style=flat)](#-testing)
[![User Service Tests](https://img.shields.io/badge/User%20Service-Tests%20Passing-brightgreen?style=flat)](#-testing)
[![Food Delivery Tests](https://img.shields.io/badge/Food%20Delivery-Tests%20Passing-brightgreen?style=flat)](#-testing)

### Running Tests

```bash
# Run all backend tests via script
./start.sh test

# Run tests for a specific service
cd backend/auth-service
./mvnw test

cd backend/food-delivery-service
./mvnw test

cd backend/user-service
./mvnw test
```

### Frontend Tests
```bash
cd frontend/travelsphere-ui
ng test
```

### CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every push and PR:
- **Backend Tests** — Unit tests for 8 services in parallel (auth, user, food, flight, hotel, notification, admin, payment)
- **Frontend Build** — Angular production build verification
- **Docker Build** — Verifies Dockerfiles build successfully
- **Badge Updates** — Dynamic shields.io badges reflect latest CI status

---

## 🌐 Service URLs

| Service | URL | Description |
|---------|-----|-------------|
| **Frontend** | http://localhost:4200 | Angular SPA |
| **API Gateway** | http://localhost:8080 | Single entry point |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | API documentation |
| **Eureka** | http://localhost:8761 | Service registry dashboard |
| **Config Server** | http://localhost:8888 | Centralized config |

### Infrastructure

| Service | URL | Description |
|---------|-----|-------------|
| PostgreSQL | `localhost:5432` | Database (user: `travelsphere`, pass: `TravelSphere@2024`) |
| Redis | `localhost:6379` | Cache & sessions (pass: `Redis@2024`) |
| Kafka | `localhost:9092` | Event streaming |
| Qdrant | `localhost:6333` | Vector database for AI/RAG |
| LocalStack | `localhost:4566` | AWS S3/Lambda emulation |
| MailHog | http://localhost:8025 | Email testing UI |

### Monitoring

| Service | URL | Description |
|---------|-----|-------------|
| Zipkin | http://localhost:9411 | Distributed tracing |
| Prometheus | http://localhost:9090 | Metrics collection |
| Grafana | http://localhost:3000 | Dashboards (admin/admin) |
| Kafka UI | http://localhost:8090 | Kafka topic browser |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Angular Frontend                         │
│                    http://localhost:4200                         │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────┐   │
│  │Flights│ │Hotels│ │ Food │ │ Cars │ │AI/Map│ │Nearby/Map│   │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          │ /api/v1/*
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                     API Gateway (8080)                          │
│              JWT Auth · Rate Limiting · Routing                 │
└─────────────────────────┬───────────────────────────────────────┘
                          │ lb://
          ┌───────────────┼───────────────────────────┐
          ▼               ▼                           ▼
   ┌─────────────┐ ┌──────────────┐          ┌──────────────┐
   │ Auth (8081) │ │ User (8082)  │    ...   │ Food (8096)  │
   │ JWT·Redis   │ │ Profile·Loy  │          │ Restaurant·  │
   │ Register    │ │ Referral     │          │ Menu·Orders  │
   └──────┬──────┘ └──────┬───────┘          └──────┬───────┘
          │               │                          │
          ▼               ▼                          ▼
   ┌──────────────────────────────────────────────────────┐
   │              PostgreSQL (5432)                        │
   │  auth_schema | user_schema | flight_schema | ...     │
   └──────────────────────────────────────────────────────┘
          │               │                          │
          ▼               ▼                          ▼
   ┌──────────────────────────────────────────────────────┐
   │           Kafka · Redis · Qdrant · LocalStack        │
   └──────────────────────────────────────────────────────┘
```

### Microservices (17 total)

| Service | Port | Description |
|---------|------|-------------|
| `service-registry` | 8761 | Netflix Eureka service discovery |
| `config-server` | 8888 | Spring Cloud Config centralized config |
| `api-gateway` | 8080 | Spring Cloud Gateway — routing, auth, rate limiting |
| `auth-service` | 8081 | JWT authentication, registration, login, password/email change |
| `user-service` | 8082 | User profiles, loyalty points, referrals, data export |
| `flight-service` | 8083 | Flight search and booking |
| `hotel-service` | 8084 | Hotel search and booking |
| `transport-service` | 8085 | Train/bus transport search and booking |
| `car-rental-service` | 8086 | Car rental search and booking |
| `insurance-service` | 8087 | Travel insurance policies and claims |
| `package-service` | 8088 | Travel packages and deals |
| `payment-service` | 8089 | Payment processing and wallet |
| `notification-service` | 8091 | Email, SMS, WebSocket push notifications |
| `document-service` | 8092 | Document management (boarding passes, invoices) |
| `search-service` | 8093 | Global search across all services |
| `ai-agent-service` | 8094 | AI chatbot, trip planner, recommendations (LangChain4j + RAG) |
| `admin-service` | 8095 | Admin dashboard, user management, analytics, fraud alerts |
| `food-delivery-service` | 8096 | Restaurant search, menu browsing, order placement & tracking |

---

## ✨ Features

### 🎫 Travel Booking
- **Flight Search & Booking** — Search flights, view details, book with seat selection
- **Hotel Search & Booking** — Search by city/date, view amenities, book rooms
- **Car Rental** — Search rental cars by location and dates
- **Transport** — Train and bus search with booking
- **Travel Packages** — Curated travel packages with itineraries
- **Insurance** — Travel insurance policies, purchase, and claims

### 🍔 Food Delivery
- **Restaurant Search** — Browse restaurants by city and cuisine
- **Menu Browsing** — View full menus with dietary filters (veg, vegan, gluten-free)
- **Order Placement** — Place food orders with delivery address and special instructions
- **Order Tracking** — Real-time order status with animated timeline (auto-refresh)
- **Order History** — View past orders and reorder

### 🤖 AI & Intelligence
- **AI Chat Agent** — Conversational AI for travel assistance
- **AI Trip Planner** — Generate complete trip itineraries based on preferences
- **AI Recommendations** — Personalized travel recommendations
- **RAG + LLM** — Retrieval-Augmented Generation with LangChain4j and Qdrant vector DB

### 📍 Location & Maps
- **Nearby Discovery** — Find nearby restaurants, hotels, cafes, attractions
- **Mini-Map Component** — Draggable floating map widget with category filters
- **Google Maps Integration** — Real-time map display, markers, info windows
- **Journey Tracker** — Track active trips on a live map

### 🔔 Real-Time Features
- **WebSocket Notifications** — Live push notifications via STOMP WebSocket
- **Notification Panel** — Dropdown panel with live status indicator
- **Order Status Updates** — Real-time food order tracking with auto-polling

### 👤 User Features
- **Authentication** — JWT-based registration, login, password reset
- **Profile Management** — View and edit profile
- **Settings** — Notifications, privacy, appearance (dark/light theme), security
- **Change Password & Email** — Secure account management
- **Loyalty Points** — Earn and track loyalty rewards
- **Referral System** — Refer friends for bonus points
- **Data Export** — Download all personal data as JSON

### 🎨 UI/UX
- **Dark Theme** — Full dark mode support across all pages
- **Responsive Design** — Mobile-first with hamburger menu
- **Material Design** — Angular Material components
- **Page Transitions** — Smooth fade-slide animations
- **Glassmorphism** — Modern glass-effect cards

### 🛡️ Admin Panel
- **Dashboard** — System metrics and KPIs
- **User Management** — Search, view, activate/deactivate users
- **Booking Management** — View and manage all bookings
- **Analytics** — Charts and reports
- **Fraud Alerts** — Suspicious activity detection
- **Support Tickets** — Customer support ticket management
- **System Health** — Service health monitoring
- **n8n Workflows** — Webhook workflow configuration

### 🏢 Enterprise Infrastructure
- **Service Discovery** — Netflix Eureka
- **Centralized Config** — Spring Cloud Config Server (local + Git)
- **API Gateway** — Spring Cloud Gateway with JWT auth filter
- **Event-Driven** — Apache Kafka for async communication
- **Caching** — Redis for sessions, token blacklist, rate limiting
- **Vector DB** — Qdrant for AI embeddings and RAG
- **Object Storage** — LocalStack S3 emulation
- **Distributed Tracing** — Zipkin
- **Monitoring** — Prometheus + Grafana dashboards
- **Containerization** — Docker Compose for all services

---

## 🔧 Configuration

### Environment Variables

The `.env` file at the project root contains all configurable values:

```env
# Database
POSTGRES_DB=travelsphere
POSTGRES_USER=travelsphere
POSTGRES_PASSWORD=TravelSphere@2024

# Redis
REDIS_PASSWORD=Redis@2024

# JWT
JWT_SECRET=TravelSphere_JWT_Secret_Key_Min32Chars_2024

# Spring Profiles
SPRING_PROFILES_ACTIVE=dev
```

### Google Maps API Key

To enable the mini-map and nearby discovery:

1. Get a Google Maps API key from [Google Cloud Console](https://console.cloud.google.com/)
2. Edit `frontend/travelsphere-ui/src/environments/environment.ts`:
   ```typescript
   googleMapsApiKey: 'YOUR_API_KEY_HERE'
   ```
3. The app works without an API key — maps show in demo mode

### Config Server

Each service is **self-contained** with its own `application.yml` — no Config Server required for local development. The Config Server is optional and can override config when running in a cloud/Docker environment. Config files in `config-repo/` are used by the Config Server for centralized configuration management.

---

## 📁 Project Structure

```
TravelSphere-Nexus-AI-Powered-Travel-Booking-Insurance-Platform/
├── backend/                        # Java Spring Boot microservices
│   ├── service-registry/           # Eureka service discovery
│   ├── config-server/              # Spring Cloud Config
│   ├── api-gateway/                # Spring Cloud Gateway
│   ├── auth-service/               # Authentication & authorization
│   ├── user-service/               # User profiles & loyalty
│   ├── flight-service/             # Flight booking
│   ├── hotel-service/              # Hotel booking
│   ├── transport-service/          # Train/bus booking
│   ├── car-rental-service/         # Car rental
│   ├── insurance-service/          # Travel insurance
│   ├── package-service/            # Travel packages
│   ├── payment-service/            # Payments & wallet
│   ├── notification-service/       # Notifications (Email/SMS/WebSocket)
│   ├── document-service/           # Document management
│   ├── search-service/             # Global search
│   ├── ai-agent-service/           # AI chatbot & trip planner
│   ├── admin-service/              # Admin panel
│   ├── food-delivery-service/      # Food delivery
│   ├── webhook-service/            # n8n webhook integration
│   └── common-lib/                 # Shared DTOs and Feign clients
├── frontend/
│   └── travelsphere-ui/            # Angular 17 SPA
│       └── src/app/
│           ├── components/         # Reusable UI components
│           │   ├── header/         # Navigation header
│           │   ├── mini-map/       # Floating Google Maps widget
│           │   └── notification-panel/  # Real-time notification dropdown
│           ├── pages/              # Page components (19 page groups)
│           ├── services/           # Angular services (17 services)
│           └── models/             # TypeScript interfaces
├── config-repo/                    # Centralized service configurations
├── infra/                          # Infrastructure scripts
│   └── postgres/init.sql           # Database schema initialization
├── .github/workflows/ci.yml        # GitHub Actions CI pipeline
├── docker-compose.yml              # Full stack orchestration
├── .env                            # Environment variables (gitignored)
├── .env.example                    # Example env file (tracked)
├── start.sh                        # Linux/macOS startup script
└── start.bat                       # Windows startup script
```

---

## 🐛 Troubleshooting

### Services won't start
```bash
# Check Docker is running
docker info

# Check container status
docker compose ps

# View logs for a specific service
docker compose logs auth-service

# Restart a specific service
docker compose restart auth-service
```

### Port already in use
```bash
# Find what's using the port (Linux/macOS)
lsof -i :8080

# Windows
netstat -ano | findstr :8080

# Stop all TravelSphere services
docker compose down
```

### Database connection issues
```bash
# Check PostgreSQL is healthy
docker compose ps postgres

# Connect to PostgreSQL directly
docker exec -it travelsphere-postgres psql -U travelsphere -d travelsphere
```

### Frontend can't reach backend
- Ensure the API Gateway is running: `docker compose ps api-gateway`
- The Angular dev server proxies `/api` to `localhost:8080` automatically
- Check the proxy config: `frontend/travelsphere-ui/proxy.conf.json`

### Reset everything
```bash
# Stop all services and remove volumes (fresh start)
docker compose down -v
./start.sh
```

---

## 📄 License

This project is for educational and demonstration purposes.
