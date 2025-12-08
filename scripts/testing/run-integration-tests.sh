#!/bin/bash

# Integration Tests Runner Script
# Day 20: Service Integration Testing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print colored message
print_message() {
    color=$1
    message=$2
    echo -e "${color}${message}${NC}"
}

# Print header
print_header() {
    echo ""
    echo "$(print_message "$BLUE" "==========================================")"
    echo "$(print_message "$BLUE" "$1")"
    echo "$(print_message "$BLUE" "==========================================")"
    echo ""
}

# Check if services are running
check_services() {
    print_header "Checking Services"

    services=(
        "http://localhost:8761/actuator/health:Eureka Server"
        "http://localhost:8888/actuator/health:Config Server"
        "http://localhost:8080/actuator/health:API Gateway"
        "http://localhost:8081/actuator/health:User Service"
        "http://localhost:8082/actuator/health:Product Service"
        "http://localhost:8083/actuator/health:Cart Service"
        "http://localhost:8084/actuator/health:Order Service"
        "http://localhost:8085/actuator/health:Payment Service"
        "http://localhost:8086/actuator/health:Inventory Service"
    )

    all_healthy=true

    for service in "${services[@]}"; do
        IFS=':' read -r url name <<< "$service"
        if curl -s -f "$url" > /dev/null 2>&1; then
            print_message "$GREEN" "✓ $name is healthy"
        else
            print_message "$RED" "✗ $name is not responding"
            all_healthy=false
        fi
    done

    if [ "$all_healthy" = false ]; then
        print_message "$YELLOW" "\nSome services are not running. Starting them..."
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
        cd "$PROJECT_ROOT" && docker-compose up -d
        print_message "$YELLOW" "Waiting 60 seconds for services to start..."
        sleep 60
        cd "$PROJECT_ROOT/integration-tests"
    else
        print_message "$GREEN" "\n✓ All services are healthy!"
    fi
}

# Run all tests
run_all_tests() {
    print_header "Running All Integration Tests"
    mvn clean test
}

# Run specific test
run_test() {
    test_class=$1
    print_header "Running $test_class"
    mvn test -Dtest="$test_class"
}

# Show menu
show_menu() {
    print_header "Integration Tests Menu"
    echo "1. Run All Tests"
    echo "2. Run End-to-End Order Flow Tests"
    echo "3. Run Saga Compensation Tests"
    echo "4. Run Concurrent Order Tests"
    echo "5. Run Performance Tests"
    echo "6. Run Load Tests (JMeter)"
    echo "7. Check Service Health"
    echo "8. View Test Results"
    echo "9. Clean Test Results"
    echo "0. Exit"
    echo ""
    read -p "Select option: " choice
}

# View test results
view_results() {
    print_header "Test Results"

    if [ -d "target/surefire-reports" ]; then
        print_message "$GREEN" "Test reports available at:"
        echo "  - target/surefire-reports/"
        echo "  - target/site/surefire-report.html (run: mvn surefire-report:report)"

        # Count tests
        total=$(grep -r "testcase" target/surefire-reports/*.xml | wc -l | tr -d ' ')
        failures=$(grep -r "failure" target/surefire-reports/*.xml | wc -l | tr -d ' ')

        echo ""
        print_message "$BLUE" "Summary:"
        echo "  Total tests: $total"
        echo "  Failures: $failures"

        if [ "$failures" -eq 0 ]; then
            print_message "$GREEN" "  Status: ✓ All tests passed!"
        else
            print_message "$RED" "  Status: ✗ Some tests failed"
        fi
    else
        print_message "$YELLOW" "No test results found. Run tests first."
    fi
}

# Clean test results
clean_results() {
    print_header "Cleaning Test Results"
    mvn clean
    print_message "$GREEN" "✓ Test results cleaned"
}

# Main execution
main() {
    # Get script directory and navigate to integration-tests
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

    cd "$PROJECT_ROOT/integration-tests"

    if [ ! -f "pom.xml" ]; then
        print_message "$RED" "Error: integration-tests/pom.xml not found."
        exit 1
    fi

    # Check services if running tests
    if [ $# -eq 0 ]; then
        # Interactive mode
        while true; do
            show_menu
            case $choice in
                1)
                    check_services
                    run_all_tests
                    ;;
                2)
                    check_services
                    run_test "OrderFlowIntegrationTest"
                    ;;
                3)
                    check_services
                    run_test "SagaCompensationTest"
                    ;;
                4)
                    check_services
                    run_test "ConcurrentOrderTest"
                    ;;
                5)
                    check_services
                    run_test "PerformanceTest"
                    ;;
                6)
                    check_services
                    run_test "LoadTestRunner"
                    ;;
                7)
                    check_services
                    ;;
                8)
                    view_results
                    ;;
                9)
                    clean_results
                    ;;
                0)
                    print_message "$BLUE" "Goodbye!"
                    exit 0
                    ;;
                *)
                    print_message "$RED" "Invalid option"
                    ;;
            esac

            echo ""
            read -p "Press Enter to continue..."
        done
    else
        # Command line mode
        case $1 in
            all)
                check_services
                run_all_tests
                ;;
            order-flow)
                check_services
                run_test "OrderFlowIntegrationTest"
                ;;
            saga)
                check_services
                run_test "SagaCompensationTest"
                ;;
            concurrent)
                check_services
                run_test "ConcurrentOrderTest"
                ;;
            performance)
                check_services
                run_test "PerformanceTest"
                ;;
            load)
                check_services
                run_test "LoadTestRunner"
                ;;
            check)
                check_services
                ;;
            results)
                view_results
                ;;
            clean)
                clean_results
                ;;
            *)
                echo "Usage: $0 [all|order-flow|saga|concurrent|performance|load|check|results|clean]"
                exit 1
                ;;
        esac
    fi
}

# Run main
main "$@"
