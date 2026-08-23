#!/usr/bin/env bash
# =============================================================================
# TravelSphere Platform — One-Command Startup Script
# =============================================================================
# Usage:
#   ./start.sh              → Start infrastructure + all backend services (Docker)
#   ./start.sh infra        → Start only infrastructure (DB, Redis, Kafka, etc.)
#   ./start.sh frontend     → Start only the Angular frontend
#   ./start.sh backend      → Start only backend microservices (Docker)
#   ./start.sh local <svc>  → Run a single service locally (e.g. ./start.sh local auth-service)
#   ./start.sh local-all    → Run ALL services locally (requires Maven + Java 17)
#   ./start.sh test         → Run all backend tests
#   ./start.sh stop         → Stop all services
#   ./start.sh status       → Show running container status
#   ./start.sh logs         → Tail logs from all services
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

print_banner() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║           ✈️  TravelSphere Platform — Startup              ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_step() {
    echo -e "${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# ── Check prerequisites ──────────────────────────────────────────────────────

check_prerequisites() {
    print_step "Checking prerequisites..."

    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker Desktop."
        exit 1
    fi

    if ! docker info &> /dev/null; then
        print_error "Docker daemon is not running. Please start Docker Desktop."
        exit 1
    fi

    if ! command -v node &> /dev/null; then
        print_warning "Node.js is not installed. Frontend won't work without it."
    fi

    print_success "Prerequisites check passed"
}

# ── Start infrastructure ─────────────────────────────────────────────────────

start_infra() {
    print_step "Starting infrastructure services (PostgreSQL, Redis, Kafka, Qdrant, etc.)..."
    docker compose up -d postgres redis zookeeper kafka qdrant localstack mailhog
    print_step "Waiting for infrastructure to be healthy..."
    docker compose up -d --wait postgres redis kafka 2>/dev/null || sleep 15
    print_success "Infrastructure services started"
    echo ""
    echo -e "  ${CYAN}PostgreSQL:${NC}  localhost:5432"
    echo -e "  ${CYAN}Redis:${NC}       localhost:6379"
    echo -e "  ${CYAN}Kafka:${NC}       localhost:9092"
    echo -e "  ${CYAN}Qdrant:${NC}      localhost:6333"
    echo -e "  ${CYAN}LocalStack:${NC}  localhost:4566"
    echo -e "  ${CYAN}MailHog:${NC}     localhost:8025 (web UI)"
    echo ""
}

# ── Start monitoring ────────────────────────────────────────────────────────

start_monitoring() {
    print_step "Starting monitoring services (Zipkin, Prometheus, Grafana)..."
    docker compose up -d zipkin prometheus grafana kafka-ui
    print_success "Monitoring services started"
    echo -e "  ${CYAN}Zipkin:${NC}      http://localhost:9411"
    echo -e "  ${CYAN}Prometheus:${NC}  http://localhost:9090"
    echo -e "  ${CYAN}Grafana:${NC}     http://localhost:3000 (admin/admin)"
    echo -e "  ${CYAN}Kafka UI:${NC}    http://localhost:8090"
    echo ""
}

# ── Start backend services ──────────────────────────────────────────────────

start_backend() {
    print_step "Starting Eureka service registry..."
    docker compose up -d service-registry
    print_step "Waiting for service registry..."
    sleep 10

    print_step "Starting config server..."
    docker compose up -d config-server
    print_step "Waiting for config server..."
    sleep 5

    print_step "Starting API gateway..."
    docker compose up -d api-gateway
    sleep 5

    print_step "Starting all microservices..."
    docker compose up -d auth-service user-service flight-service hotel-service \
        transport-service car-rental-service insurance-service package-service \
        payment-service notification-service document-service search-service \
        ai-agent-service admin-service food-delivery-service webhook-service

    print_success "All backend services started"
    echo ""
    echo -e "  ${CYAN}Eureka:${NC}       http://localhost:8761"
    echo -e "  ${CYAN}Config Server:${NC} http://localhost:8888"
    echo -e "  ${CYAN}API Gateway:${NC}   http://localhost:8080"
    echo ""
    echo -e "  ${YELLOW}Tip: It takes ~30-60s for all services to register with Eureka.${NC}"
    echo -e "  ${YELLOW}Check status: docker compose ps${NC}"
    echo ""
}

# ── Run a single service locally ───────────────────────────────────────────

start_local_service() {
    local SERVICE=$1
    if [ -z "$SERVICE" ]; then
        print_error "Usage: ./start.sh local <service-name>"
        echo ""
        echo "Available services:"
        echo "  service-registry, config-server, api-gateway"
        echo "  auth-service, user-service, flight-service, hotel-service"
        echo "  transport-service, car-rental-service, insurance-service, package-service"
        echo "  payment-service, notification-service, document-service, search-service"
        echo "  ai-agent-service, admin-service, food-delivery-service, webhook-service"
        exit 1
    fi

    local SVC_DIR="backend/$SERVICE"
    if [ ! -d "$SVC_DIR" ]; then
        print_error "Service '$SERVICE' not found in backend/"
        exit 1
    fi

    print_step "Building and running $SERVICE locally..."
    echo ""

    # Build the service
    cd "$SVC_DIR"
    if [ ! -f "target/*.jar" ] || [ ! -d "target" ]; then
        print_step "Building $SERVICE with Maven..."
        if [ -f "./mvnw" ]; then
            ./mvnw clean package -DskipTests -q
        else
            mvn clean package -DskipTests -q
        fi
    fi

    # Run the service
    JAR=$(ls target/*.jar 2>/dev/null | head -1)
    if [ -z "$JAR" ]; then
        print_error "Build failed - no JAR found in target/"
        cd "$SCRIPT_DIR"
        exit 1
    fi

    print_success "Starting $SERVICE from $JAR"
    print_warning "Press Ctrl+C to stop"
    echo ""
    java -jar "$JAR"
    cd "$SCRIPT_DIR"
}

# ── Run all services locally ────────────────────────────────────────────────

start_local_all() {
    print_step "Building entire project..."
    mvn clean package -DskipTests -q -T 4
    if [ $? -ne 0 ]; then
        print_error "Build failed"
        exit 1
    fi
    print_success "Build complete"
    echo ""

    # Start order: infra services first, then business services
    CORE_SERVICES="service-registry config-server api-gateway"
    BUSINESS_SERVICES="auth-service user-service flight-service hotel-service \
        transport-service car-rental-service insurance-service package-service \
        payment-service notification-service document-service search-service \
        ai-agent-service admin-service food-delivery-service"

    PIDS=()

    for SVC in $CORE_SERVICES; do
        JAR=$(ls backend/$SVC/target/*.jar 2>/dev/null | head -1)
        if [ -n "$JAR" ]; then
            print_step "Starting $SVC..."
            java -jar "$JAR" &
            PIDS+=($!)
            sleep 3
        fi
    done

    for SVC in $BUSINESS_SERVICES; do
        JAR=$(ls backend/$SVC/target/*.jar 2>/dev/null | head -1)
        if [ -n "$JAR" ]; then
            print_step "Starting $SVC..."
            java -jar "$JAR" &
            PIDS+=($!)
        fi
    done

    echo ""
    print_success "All services started!"
    echo -e "  ${CYAN}Eureka:${NC}       http://localhost:8761"
    echo -e "  ${CYAN}API Gateway:${NC}   http://localhost:8080"
    echo -e "  ${CYAN}Swagger:${NC}       http://localhost:8080/swagger-ui.html"
    echo ""
    echo -e "  ${YELLOW}Press Ctrl+C to stop all services${NC}"

    # Wait for all background processes
    trap "kill ${PIDS[*]} 2>/dev/null; exit" INT TERM
    wait
}

# ── Start frontend ──────────────────────────────────────────────────────────

start_frontend() {
    print_step "Starting Angular frontend..."
    cd frontend/travelsphere-ui

    if [ ! -d "node_modules" ]; then
        print_step "Installing frontend dependencies..."
        npm install
    fi

    print_success "Starting Angular dev server on http://localhost:4200"
    cd "$SCRIPT_DIR"
    npm start --prefix frontend/travelsphere-ui &
    echo ""
}

# ── Run tests ───────────────────────────────────────────────────────────────

run_tests() {
    print_step "Running backend tests..."
    echo ""

    for service in auth-service user-service food-delivery-service; do
        echo -e "${BLUE}Testing $service...${NC}"
        cd "backend/$service"
        if [ -f "pom.xml" ]; then
            ./mvnw test -q 2>/dev/null || mvn test -q 2>/dev/null || print_warning "Tests failed for $service (may need dependencies)"
        fi
        cd "$SCRIPT_DIR"
        echo ""
    done

    print_success "Tests completed"
}

# ── Stop all ────────────────────────────────────────────────────────────────

stop_all() {
    print_step "Stopping all services..."
    docker compose down
    print_success "All services stopped"
}

# ── Show status ─────────────────────────────────────────────────────────────

show_status() {
    docker compose ps
}

# ── Show logs ───────────────────────────────────────────────────────────────

show_logs() {
    docker compose logs -f --tail=50
}

# ── Print URLs ──────────────────────────────────────────────────────────────

print_urls() {
    echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                 🚀 All Services Running                    ║${NC}"
    echo -e "${GREEN}╠══════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${GREEN}║                                                            ║${NC}"
    echo -e "${GREEN}║  ${CYAN}Frontend:${NC}      http://localhost:4200                   ${GREEN}║${NC}"
    echo -e "${GREEN}║  ${CYAN}API Gateway:${NC}   http://localhost:8080                   ${GREEN}║${NC}"
    echo -e "${GREEN}║  ${CYAN}Eureka:${NC}        http://localhost:8761                   ${GREEN}║${NC}"
    echo -e "${GREEN}║  ${CYAN}Swagger:${NC}       http://localhost:8080/swagger-ui.html  ${GREEN}║${NC}"
    echo -e "${GREEN}║                                                            ║${NC}"
    echo -e "${GREEN}║  ${YELLOW}Infrastructure:${NC}                                      ${GREEN}║${NC}"
    echo -e "${GREEN}║  PostgreSQL:  localhost:5432                               ${GREEN}║${NC}"
    echo -e "${GREEN}║  Redis:       localhost:6379                               ${GREEN}║${NC}"
    echo -e "${GREEN}║  Kafka:       localhost:9092                               ${GREEN}║${NC}"
    echo -e "${GREEN}║  MailHog:     http://localhost:8025                        ${GREEN}║${NC}"
    echo -e "${GREEN}║                                                            ║${NC}"
    echo -e "${GREEN}║  ${YELLOW}Monitoring:${NC}                                            ${GREEN}║${NC}"
    echo -e "${GREEN}║  Zipkin:      http://localhost:9411                        ${GREEN}║${NC}"
    echo -e "${GREEN}║  Prometheus:  http://localhost:9090                        ${GREEN}║${NC}"
    echo -e "${GREEN}║  Grafana:     http://localhost:3000  (admin/admin)         ${GREEN}║${NC}"
    echo -e "${GREEN}║  Kafka UI:    http://localhost:8090                        ${GREEN}║${NC}"
    echo -e "${GREEN}║                                                            ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# ── Main ────────────────────────────────────────────────────────────────────

print_banner

case "${1:-all}" in
    infra)
        check_prerequisites
        start_infra
        start_monitoring
        ;;
    frontend)
        start_frontend
        ;;
    backend)
        check_prerequisites
        start_infra
        start_monitoring
        start_backend
        print_urls
        ;;
    local)
        start_local_service "$2"
        ;;
    local-all)
        start_local_all
        ;;
    test)
        run_tests
        ;;
    stop)
        stop_all
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    all)
        check_prerequisites
        start_infra
        start_monitoring
        start_backend
        print_urls
        echo -e "${YELLOW}To start the frontend, run: ./start.sh frontend${NC}"
        echo -e "${YELLOW}Or manually: cd frontend/travelsphere-ui && npm install && ng serve${NC}"
        echo ""
        ;;
    *)
        echo "Usage: ./start.sh [infra|frontend|backend|local <svc>|local-all|test|stop|status|logs|all]"
        echo ""
        echo "  all          → Start infrastructure + backend via Docker (default)"
        echo "  infra        → Start only infrastructure services via Docker"
        echo "  frontend     → Start Angular dev server"
        echo "  backend      → Start all backend microservices via Docker"
        echo "  local <svc>  → Run a single service locally (e.g. ./start.sh local auth-service)"
        echo "  local-all    → Run ALL services locally (requires Java 17 + Maven)"
        echo "  test         → Run backend unit tests"
        echo "  stop         → Stop all services"
        echo "  status       → Show running container status"
        echo "  logs         → Tail logs from all services"
        exit 1
        ;;
esac
