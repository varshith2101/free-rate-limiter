#!/bin/bash

################################################################################
#                    Rate Limiter - Comprehensive Launch Script
# 
# This script provides multiple ways to launch the rate limiter services:
# - Docker Compose (production-ready)
# - Local Development (backend + frontend separately)
# - Individual service control
#
# Usage: ./launch-services.sh [command] [options]
################################################################################

set -o pipefail

# Script directory
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
COMPOSE_CMD=""
JAVA_VERSION="21"
NODE_VERSION="18"
BACKEND_PORT="8081"
FRONTEND_PORT="3000"
DASHBOARD_PORT="3000"
POSTGRES_PORT="5432"
REDIS_PORT="6379"

################################################################################
# Utility Functions
################################################################################

print_header() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

resolve_host() {
    local host=$1
    if command -v getent >/dev/null 2>&1; then
        getent hosts "$host" >/dev/null 2>&1
        return $?
    fi
    if command -v nslookup >/dev/null 2>&1; then
        nslookup "$host" >/dev/null 2>&1
        return $?
    fi
    if command -v dig >/dev/null 2>&1; then
        dig +short "$host" | grep -q .
        return $?
    fi
    return 1
}

check_docker_registry_access() {
    local registry_host="registry-1.docker.io"
    local url="https://$registry_host/v2/"

    if ! resolve_host "$registry_host"; then
        print_error "DNS resolution failed for $registry_host"
        return 1
    fi

    if command -v curl >/dev/null 2>&1; then
        if ! curl -sS --connect-timeout 5 --max-time 10 -I "$url" >/dev/null 2>&1; then
            print_error "Unable to reach Docker registry at $url"
            return 1
        fi
    fi

    return 0
}

check_command() {
    if ! command -v "$1" &> /dev/null; then
        return 1
    fi
    return 0
}

setup_compose_cmd() {
    if check_command docker-compose; then
        COMPOSE_CMD="docker-compose"
    elif check_command docker && docker compose version &> /dev/null 2>&1; then
        COMPOSE_CMD="docker compose"
    else
        print_error "Docker Compose not found"
        print_info "Please install Docker Compose: https://docs.docker.com/compose/install/"
        return 1
    fi
    return 0
}

check_port_available() {
    local port=$1
    local service=$2
    
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 1
    fi
    return 0
}

wait_for_service() {
    local url=$1
    local service=$2
    local timeout=${3:-120}
    local elapsed=0
    
    print_info "Waiting for $service to be healthy..."
    
    while [ $elapsed -lt $timeout ]; do
        if curl -s -f "$url" > /dev/null 2>&1; then
            print_success "$service is healthy"
            return 0
        fi
        
        sleep 2
        elapsed=$((elapsed + 2))
        printf "."
    done
    
    echo ""
    print_error "$service did not become healthy within ${timeout}s"
    return 1
}

display_banner() {
    clear
    echo -e "${CYAN}"
    cat << "EOF"
╔═══════════════════════════════════════════════════════════╗
║                                                           ║
║       🚀 Self-Hosted Rate Limiter Platform 🚀            ║
║                                                           ║
║           Launch & Service Management Script              ║
║                                                           ║
╚═══════════════════════════════════════════════════════════╝
EOF
    echo -e "${NC}"
}

################################################################################
# Docker Compose Mode
################################################################################

cmd_docker_up() {
    print_header "Starting Services with Docker Compose"
    
    # Check Docker
    if ! check_command docker; then
        print_error "Docker is not installed"
        print_info "Install Docker: https://docs.docker.com/get-docker/"
        return 1
    fi
    
    print_success "Docker is installed"
    
    # Setup compose command
    if ! setup_compose_cmd; then
        return 1
    fi
    
    print_success "Using: $COMPOSE_CMD"
    echo ""

    # Preflight registry connectivity
    if ! check_docker_registry_access; then
        print_warning "Docker registry is unreachable from this machine."
        print_info "Common fixes:"
        print_info "1) If you're behind a corporate proxy, configure Docker Desktop > Settings > Resources > Proxies"
        print_info "2) Set HTTPS proxy env vars for Docker Desktop or your shell (HTTPS_PROXY/NO_PROXY)"
        print_info "3) Check your DNS or VPN settings and try again"
        print_info "You can also run: docker info | grep -i 'proxy' to see current Docker proxy settings"
        return 1
    fi

    # Create .env if needed
    if [ ! -f "$BASE_DIR/.env" ]; then
        if [ -f "$BASE_DIR/.env.example" ]; then
            print_warning ".env file not found, creating from template..."
            cp "$BASE_DIR/.env.example" "$BASE_DIR/.env"
            print_success "Created .env file"
            print_warning "Review and update .env with your configuration"
        fi
    fi
    
    # Build and start
    print_info "Building and starting services..."
    echo ""
    
    cd "$BASE_DIR"
    if ! $COMPOSE_CMD up -d --build; then
        print_error "Failed to start services"
        return 1
    fi
    
    print_success "Services started"
    echo ""
    
    # Wait for backend via gateway (single entrypoint)
    if ! wait_for_service "http://localhost/actuator/health" "Backend API" 120; then
        print_error "Backend failed to start. Checking logs..."
        $COMPOSE_CMD logs backend | tail -50
        return 1
    fi
    
    # Display service info
    cmd_docker_status
    
    # Display API keys and access info
    display_access_info
    
    return 0
}

cmd_docker_down() {
    print_header "Stopping Services"
    
    if ! setup_compose_cmd; then
        return 1
    fi
    
    cd "$BASE_DIR"
    
    print_info "Stopping all services..."
    if $COMPOSE_CMD down; then
        print_success "Services stopped"
    else
        print_error "Failed to stop services"
        return 1
    fi
    
    return 0
}

cmd_docker_status() {
    print_header "Service Status"
    
    if ! setup_compose_cmd; then
        return 1
    fi
    
    cd "$BASE_DIR"
    $COMPOSE_CMD ps
    
    echo ""
}

cmd_docker_logs() {
    local service=$1
    
    if ! setup_compose_cmd; then
        return 1
    fi
    
    cd "$BASE_DIR"
    
    if [ -z "$service" ]; then
        print_info "Showing logs for all services (Ctrl+C to exit)..."
        $COMPOSE_CMD logs -f
    else
        print_info "Showing logs for $service (Ctrl+C to exit)..."
        $COMPOSE_CMD logs -f "$service"
    fi
    
    return 0
}

cmd_docker_clean() {
    print_header "Cleaning Up Services & Data"
    
    if ! setup_compose_cmd; then
        return 1
    fi
    
    print_warning "This will remove containers, networks, and volumes (database data)"
    read -p "Are you sure? (yes/no): " -r
    echo ""
    
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        print_info "Cleanup cancelled"
        return 0
    fi
    
    cd "$BASE_DIR"
    print_info "Removing containers and volumes..."
    
    if $COMPOSE_CMD down -v; then
        print_success "Services and data cleaned up"
    else
        print_error "Failed to cleanup"
        return 1
    fi
    
    return 0
}

cmd_docker_restart() {
    print_header "Restarting Services"
    
    if ! setup_compose_cmd; then
        return 1
    fi
    
    cd "$BASE_DIR"
    print_info "Restarting services..."
    
    if $COMPOSE_CMD restart; then
        print_success "Services restarted"
    else
        print_error "Failed to restart services"
        return 1
    fi
    
    wait_for_service "http://localhost:$BACKEND_PORT/actuator/health" "Backend API" 60
    return 0
}

################################################################################
# Local Development Mode
################################################################################

cmd_local_setup() {
    print_header "Local Development Setup Check"
    
    local missing=0
    
    # Check Java
    if check_command java; then
        java_version=$(java -version 2>&1 | grep -E 'version' | sed 's/.*version "\([^"]*\)".*/\1/' | head -1)
        print_success "Java $java_version found"
    else
        print_error "Java not found (required: Java 21+)"
        missing=1
    fi
    
    # Check Node
    if check_command node; then
        node_version=$(node --version)
        print_success "Node $node_version found"
    else
        print_error "Node.js not found (required: Node 18+)"
        missing=1
    fi
    
    # Check Maven
    if check_command mvn; then
        mvn_version=$(mvn --version 2>&1 | head -1)
        print_success "Maven installed: $mvn_version"
    else
        print_error "Maven not found"
        missing=1
    fi
    
    # Check PostgreSQL
    if check_command psql; then
        print_success "PostgreSQL client found"
    else
        print_warning "PostgreSQL client not found (optional if using Docker)"
    fi
    
    # Check Redis
    if check_command redis-cli; then
        print_success "Redis CLI found"
    else
        print_warning "Redis CLI not found (optional if using Docker)"
    fi
    
    echo ""
    
    if [ $missing -eq 1 ]; then
        print_error "Some required tools are missing"
        print_info "See DEPLOYMENT.md for installation instructions"
        return 1
    fi
    
    print_success "All required tools are installed"
    return 0
}

cmd_local_backend_only() {
    print_header "Starting Backend Only (Local Development)"
    
    if ! cmd_local_setup; then
        return 1
    fi
    
    # Check if database is running
    if ! check_port_available $POSTGRES_PORT "PostgreSQL"; then
        print_warning "PostgreSQL already running on port $POSTGRES_PORT"
    else
        print_info "Starting PostgreSQL and Redis in Docker..."
        if ! setup_compose_cmd; then
            print_error "Docker Compose required for database/redis"
            return 1
        fi
        
        cd "$BASE_DIR"
        $COMPOSE_CMD up -d postgres redis
        sleep 5
        print_success "Database services started"
    fi
    
    # Start backend
    print_info "Building and starting Spring Boot backend..."
    cd "$BASE_DIR/ratelimiter"
    
    if ! mvn clean install -DskipTests; then
        print_error "Maven build failed"
        return 1
    fi
    
    print_success "Backend started on http://localhost:$BACKEND_PORT"
    wait_for_service "http://localhost:$BACKEND_PORT/actuator/health" "Backend API"
    
    return 0
}

cmd_local_frontend_only() {
    print_header "Starting Frontend Only (Local Development)"
    
    if ! check_command node; then
        print_error "Node.js not found"
        return 1
    fi
    
    if ! check_command npm; then
        print_error "npm not found"
        return 1
    fi
    
    cd "$BASE_DIR/dashboard"
    
    print_info "Installing dependencies..."
    if ! npm install; then
        print_error "Failed to install dependencies"
        return 1
    fi
    
    print_info "Starting Vite development server..."
    print_success "Frontend available at http://localhost:$DASHBOARD_PORT"
    npm run dev
    
    return 0
}

cmd_local_full() {
    print_header "Starting All Services (Local Development)"
    
    if ! cmd_local_setup; then
        return 1
    fi
    
    # Start database services in background
    if setup_compose_cmd > /dev/null 2>&1; then
        print_info "Starting PostgreSQL and Redis..."
        cd "$BASE_DIR"
        
        if ! check_port_available $POSTGRES_PORT "PostgreSQL"; then
            print_warning "PostgreSQL already running"
        else
            $COMPOSE_CMD up -d postgres redis
            sleep 5
        fi
    else
        print_warning "Docker Compose not available, assuming PostgreSQL/Redis are running"
    fi
    
    # Start backend in background
    print_info "Starting backend (in background)..."
    cd "$BASE_DIR/ratelimiter"
    
    # Check if already running
    if check_port_available $BACKEND_PORT "Backend"; then
        mvn spring-boot:run &
        BACKEND_PID=$!
        
        if wait_for_service "http://localhost:$BACKEND_PORT/actuator/health" "Backend API" 60; then
            print_success "Backend started (PID: $BACKEND_PID)"
        fi
    else
        print_warning "Backend already running on port $BACKEND_PORT"
    fi
    
    # Start frontend
    print_info "Starting frontend..."
    cd "$BASE_DIR/dashboard"
    
    if ! npm install > /dev/null 2>&1; then
        print_error "Failed to install frontend dependencies"
        return 1
    fi
    
    print_success "Frontend starting on http://localhost:$DASHBOARD_PORT"
    npm run dev
    
    return 0
}

################################################################################
# Testing & Validation
################################################################################

cmd_health_check() {
    print_header "Health Check"
    
    local errors=0
    
    # Check backend
    if check_port_available $BACKEND_PORT "Backend"; then
        print_warning "Backend API not responding on port $BACKEND_PORT"
        errors=$((errors + 1))
    else
        if curl -s -f "http://localhost:$BACKEND_PORT/actuator/health" > /dev/null 2>&1; then
            print_success "Backend API healthy"
        else
            print_error "Backend API unhealthy"
            errors=$((errors + 1))
        fi
    fi
    
    # Check frontend
    if check_port_available $DASHBOARD_PORT "Frontend"; then
        print_warning "Frontend not responding on port $DASHBOARD_PORT"
    else
        print_success "Frontend responding on port $DASHBOARD_PORT"
    fi
    
    # Check database ports
    if ! check_port_available $POSTGRES_PORT "PostgreSQL"; then
        print_success "PostgreSQL running on port $POSTGRES_PORT"
    else
        print_warning "PostgreSQL not found on port $POSTGRES_PORT"
    fi
    
    if ! check_port_available $REDIS_PORT "Redis"; then
        print_success "Redis running on port $REDIS_PORT"
    else
        print_warning "Redis not found on port $REDIS_PORT"
    fi
    
    echo ""
    
    if [ $errors -eq 0 ]; then
        print_success "All services are healthy"
        return 0
    else
        print_error "Some services are unhealthy"
        return 1
    fi
}

################################################################################
# Information & Help
################################################################################

display_access_info() {
    print_header "Access Information"
    
    echo -e "${GREEN}Web Interfaces:${NC}"
    echo "  Dashboard:     ${CYAN}http://localhost:3000${NC}"
    echo "  Backend API:   ${CYAN}http://localhost:8081${NC}"
    echo "  Health Check:  ${CYAN}http://localhost:8081/actuator/health${NC}"
    echo "  Metrics:       ${CYAN}http://localhost:8081/actuator/prometheus${NC}"
    echo ""
    
    echo -e "${GREEN}Database Credentials:${NC}"
    echo "  PostgreSQL:    localhost:5432 (user: ratelimiter)"
    echo "  Redis:         localhost:6379"
    echo ""
}

show_help() {
    display_banner
    
    cat << EOF
${CYAN}USAGE:${NC}
  ./launch-services.sh [command] [options]

${CYAN}DOCKER COMPOSE COMMANDS:${NC}
  up              Start all services with Docker Compose
  down            Stop all services
  status          Show service status
  logs [service]  View service logs (service: postgres, redis, backend, dashboard)
  restart         Restart services
  clean           Remove containers, networks, and volumes

${CYAN}LOCAL DEVELOPMENT COMMANDS:${NC}
  setup           Check local development environment
  backend         Start backend only (requires PostgreSQL + Redis in Docker)
  frontend        Start frontend only (dev server on port 3000)
  full            Start all services locally (backend + frontend)

${CYAN}UTILITY COMMANDS:${NC}
  health          Check health of running services
  info            Display access information and service details
  help            Show this help message

${CYAN}EXAMPLES:${NC}
  # Start everything with Docker Compose
  ./launch-services.sh up

  # Check service status
  ./launch-services.sh status

  # View backend logs
  ./launch-services.sh logs backend

  # Local development
  ./launch-services.sh setup        # Check prerequisites
  ./launch-services.sh backend      # Start backend
  ./launch-services.sh frontend     # Start frontend (separate terminal)

    # Test
    ./launch-services.sh health

${CYAN}ENVIRONMENT VARIABLES:${NC}
  BACKEND_PORT    Backend port (default: 8081)
  FRONTEND_PORT   Frontend port (default: 3000)

EOF
}

################################################################################
# Main Execution
################################################################################

main() {
    local command=${1:-help}
    
    case "$command" in
        # Docker Compose
        up|start)
            cmd_docker_up
            ;;
        down|stop)
            cmd_docker_down
            ;;
        status|ps)
            cmd_docker_status
            ;;
        logs)
            cmd_docker_logs "$2"
            ;;
        restart)
            cmd_docker_restart
            ;;
        clean|reset)
            cmd_docker_clean
            ;;
        
        # Local Development
        setup)
            cmd_local_setup
            ;;
        backend)
            cmd_local_backend_only
            ;;
        frontend)
            cmd_local_frontend_only
            ;;
        full|local)
            cmd_local_full
            ;;
        
        # Utilities
        health)
            cmd_health_check
            ;;
        info)
            display_banner
            display_access_info
            ;;
        help|-h|--help)
            show_help
            ;;
        *)
            print_error "Unknown command: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
    
    exit $?
}

main "$@"
