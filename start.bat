@echo off
REM =============================================================================
REM TravelSphere Platform — Windows Startup Script
REM =============================================================================
REM Usage:
REM   start.bat              → Start infrastructure + all backend services
REM   start.bat infra        → Start only infrastructure
REM   start.bat frontend     → Start only the Angular frontend
REM   start.bat backend      → Start only backend microservices
REM   start.bat local <svc>  → Run a single service locally (e.g. start.bat local auth-service)
REM   start.bat stop         → Stop all services
REM   start.bat status       → Show running container status
REM =============================================================================

echo.
echo ======================================
echo    TravelSphere Platform - Startup
echo ======================================
echo.

REM Check Docker
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not running. Please start Docker Desktop.
    pause
    exit /b 1
)

if "%1"=="stop" (
    echo Stopping all services...
    docker compose down
    echo Done.
    goto :end
)

if "%1"=="status" (
    docker compose ps
    goto :end
)

if "%1"=="logs" (
    docker compose logs -f --tail=50
    goto :end
)

if "%1"=="frontend" (
    echo Starting Angular frontend...
    cd frontend\travelsphere-ui
    if not exist "node_modules" (
        echo Installing dependencies...
        call npm install
    )
    call npm run start
    cd ..\..
    goto :end
)

if "%1"=="test" (
    echo Running backend tests...
    echo Testing auth-service...
    cd backend\auth-service && call mvnw.cmd test -q 2>nul || call mvn test -q 2>nul
    cd ..\..
    echo Testing user-service...
    cd backend\user-service && call mvnw.cmd test -q 2>nul || call mvn test -q 2>nul
    cd ..\..
    echo Testing food-delivery-service...
    cd backend\food-delivery-service && call mvnw.cmd test -q 2>nul || call mvn test -q 2>nul
    cd ..\..
    echo Tests completed.
    goto :end
)

if "%1"=="local" (
    if "%2"=="" (
        echo Usage: start.bat local ^<service-name^>
        echo.
        echo Available services:
        echo   service-registry, config-server, api-gateway
        echo   auth-service, user-service, flight-service, hotel-service
        echo   transport-service, car-rental-service, insurance-service, package-service
        echo   payment-service, notification-service, document-service, search-service
        echo   ai-agent-service, admin-service, food-delivery-service
        goto :end
    )
    echo Building and running %2 locally...
    cd backend\%2
    if not exist "target" (
        echo Building with Maven...
        call mvnw.cmd clean package -DskipTests -q 2>nul || call mvn clean package -DskipTests -q
    )
    for /f "delims=" %%i in ('dir /b target\*.jar 2^>nul') do set JAR=target\%%i
    if "%JAR%"=="" (
        echo [ERROR] Build failed - no JAR found
        cd ..\..\r
        goto :end
    )
    echo Starting %2 from %JAR%...
    java -jar "%JAR%"
    cd ..\..\r
    goto :end
)

if "%1"=="infra" (
    echo Starting infrastructure services...
    docker compose up -d postgres redis zookeeper kafka qdrant localstack mailhog
    echo Waiting for infrastructure...
    timeout /t 15 /nobreak >nul
    echo Starting monitoring...
    docker compose up -d zipkin prometheus grafana kafka-ui
    echo.
    echo Infrastructure started:
    echo   PostgreSQL:  localhost:5432
    echo   Redis:       localhost:6379
    echo   Kafka:       localhost:9092
    echo   Qdrant:      localhost:6333
    echo   MailHog UI:  http://localhost:8025
    echo   Zipkin:      http://localhost:9411
    echo   Grafana:     http://localhost:3000
    goto :end
)

if "%1"=="backend" (
    echo Starting all backend services...
    docker compose up -d
    echo.
    echo Services starting... (takes 30-60 seconds)
    echo   Frontend:     http://localhost:4200
    echo   API Gateway:  http://localhost:8080
    echo   Eureka:       http://localhost:8761
    echo   Swagger:      http://localhost:8080/swagger-ui.html
    goto :end
)

REM Default: start everything
echo Starting all infrastructure and backend services...
docker compose up -d
echo.
echo ======================================
echo   All services starting...
echo   (Takes 30-60 seconds to fully boot)
echo ======================================
echo.
echo   API Gateway:  http://localhost:8080
echo   Eureka:       http://localhost:8761
echo   Swagger:      http://localhost:8080/swagger-ui.html
echo.
echo   To start frontend:
echo     cd frontend\travelsphere-ui
echo     npm install
echo     ng serve
echo.
echo   To run a service locally (no Docker for backend):
echo     start.bat local auth-service
echo     start.bat local api-gateway
echo.
echo   Check status: docker compose ps
echo   Stop:         docker compose down
echo.

:end
pause
