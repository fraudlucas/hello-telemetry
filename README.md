# hello-telemetry

Study project on **OpenTelemetry**, forked from [brainbsod/hello-telemetry](https://github.com/brainbsod/hello-telemetry).

Based on the Udemy course **"[OpenTelemetry Foundations: Hands-On Observability + OTCA](https://www.udemy.com/course/opentelemetry-foundations/)"**.

## Overview

A multi-service application instrumented with OpenTelemetry to demonstrate distributed tracing:

- **Java Servlet** (Tomcat) — serves a web page, queries MySQL and calls a Python microservice
- **Python (Flask) microservice** — computes the average age from the data
- **MySQL** — pre-populated database with sample records
- **OpenTelemetry Collector** — receives traces/metrics/logs via OTLP and forwards them to Jaeger
- **Jaeger** — stores and visualizes traces

## Branches

Each numbered branch follows a section of the Udemy course:

| Branch | Course Section | Description |
|--------|---------------|-------------|
| `main` | — | Starting point: multi-service app without instrumentation |
| `01-zerocode` | Zero-code instrumentation | OpenTelemetry Java agent + Python auto-instrumentation (no code changes) |
| `02-code-based-metrics` | Code-based instrumentation | Manual custom spans, metrics, and baggage propagation |

To switch to a branch:

```bash
git checkout <branch-name>
```

## Requirements

- Docker & Docker Compose
- Java 21+ and Maven (only if rebuilding the WAR)
- Python 3.13+ (only for local development)

## Quick Start

```bash
# 1. Clone the repository
git clone <your-fork-url>
cd hello-telemetry

# 2. (Optional) Rebuild the WAR file after modifying Java code
cd java-app && mvn clean package && cd ..

# 3. Start all services
docker compose up -d

# 4. Open in your browser
#    Application:  http://localhost:8085
#    Jaeger UI:    http://localhost:16686
```

All ports can be customized in the [`.env`](./.env) file.

## Services

| Service            | Container          | Host Ports                    |
|--------------------|--------------------|-------------------------------|
| Tomcat (Java App)  | `ht-tomcat`        | `8085` (web), `9010` (JMX)   |
| Python Service     | `ht-python-service`| `5001`                        |
| MySQL              | `ht-mysql`         | `3306`                        |
| OTel Collector     | `ht-otel-collector`| `4317`/`4318` (OTLP), etc.   |
| Jaeger             | `ht-jaeger`        | `16686` (UI)                  |

## Project Structure

```
hello-telemetry/
├── docker-compose.yml           # Orchestrates all containers
├── .env                         # Environment variable defaults
├── java-app/                    # Java Servlet application + pom.xml
├── python-service/              # Flask microservice
├── tomcat/                      # Tomcat Dockerfile + pre-built WAR
├── initdb/                      # Database initialization script
├── otel-collector-config.yaml   # OpenTelemetry Collector configuration
├── jaeger-config.yaml           # Jaeger storage and pipeline config
└── LICENSE                      # Apache 2.0
```
