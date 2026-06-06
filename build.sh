#!/bin/bash

echo "Building Canadian Billing System..."

# Build with Maven
mvn clean install -DskipTests

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo ""
    echo "To run the application:"
    echo "  mvn spring-boot:run"
    echo ""
    echo "To build Docker image:"
    echo "  docker build -t accounting:1.0 ."
    echo ""
    echo "To run with Docker Compose:"
    echo "  docker-compose up"
else
    echo "Build failed!"
    exit 1
fi

