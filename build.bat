@echo off

echo Building Canadian Billing System...

REM Build with Maven
mvn clean install -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful!
    echo.
    echo To run the application:
    echo   mvn spring-boot:run
    echo.
    echo To build Docker image:
    echo   docker build -t accounting:1.0 .
    echo.
    echo To run with Docker Compose:
    echo   docker-compose up
    echo.
) else (
    echo Build failed!
    exit /b 1
)

