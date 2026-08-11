# 4mulaQuery — Intelligent Database Engine

<div align="center">

![4mulaQuery Logo](assets/Logo.jpg)

**A production-grade database engine built from scratch in C++ with B+ Tree indexing, Java Spring Boot REST API, MongoDB-backed authentication, AI-powered CSV import, real-time analytics, and live cloud deployment.**

[![Live Demo](https://img.shields.io/badge/Live-Demo-gold?style=for-the-badge)](https://fourmulaquery.onrender.com)
[![GitHub](https://img.shields.io/badge/GitHub-4mulaMind-blue?style=for-the-badge&logo=github)](https://github.com/4mulaMind/4mulaQuery)
[![Dev.to](https://img.shields.io/badge/Dev.to-Article-black?style=for-the-badge)](https://dev.to/qadir21)

</div>

---

## What is 4mulaQuery?

4mulaQuery is a fully functional relational database engine built entirely from scratch — no SQLite, no MySQL, no existing database library. It implements core database engineering concepts at the binary level while exposing a modern full-stack interface.

**The key differentiator:** Unlike other educational database projects, 4mulaQuery is a complete production-deployed system with AI-powered data ingestion, persistent cloud authentication, real-time analytics, and machine learning anomaly detection — all integrated into a single coherent platform.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              Browser / Client                        │
│         fourmulaquery.onrender.com                   │
└─────────────────────┬───────────────────────────────┘
                      │ HTTP REST
┌─────────────────────▼───────────────────────────────┐
│           Java 17 + Spring Boot 3.2                  │
│  ApiController  │  AuthController  │  EngineService  │
│  UserStore      │  QueryLogger     │  StreamHandler   │
│  FlexibleRecord │  UserRepository  │  ProcessManager  │
└──────┬──────────────────┬──────────────────┬─────────┘
       │ subprocess        │ MongoDB Atlas     │ JavaMail
       │ stdin/stdout      │ (persistent)      │ (OTP)
┌──────▼──────┐   ┌───────▼────────┐
│ C++ Engine  │   │  MongoDB Atlas  │
│ B+ Tree     │   │  users          │
│ Binary I/O  │   │  flexible_records│
│ CRUD ops    │   │  (CSV datasets) │
└──────┬──────┘   └────────────────┘
       │
┌──────▼──────┐
│ 4mulaQuery  │
│   .db file  │
│ (binary)    │
└─────────────┘
```

---

## Features

### Core Database Engine (C++)
- **B+ Tree Indexing** — O(log n) insert, search, delete operations
- **Binary File Storage** — Custom 4KB page-based disk I/O
- **CRUD Operations** — INSERT, SEARCH, DELETE, SELECT ALL
- **Fixed-width Schema** — id (4B) + username (32B) + email (255B) = 291 bytes/record
- **Root Page Persistence** — B+ Tree survives process restarts

### Java Spring Boot API Layer
- **REST Endpoints** — `/api/insert`, `/api/search`, `/api/delete`, `/api/all`, `/api/logs`
- **IPC Bridge** — Java spawns C++ as subprocess via ProcessBuilder
- **Single Responsibility** — 8 dedicated service classes
- **CSV Import** — Fixed schema `/api/import/csv`
- **Flexible CSV Import** — Any schema `/api/import/flexible` → MongoDB
- **Dataset API** — `/api/datasets`, `/api/dataset/{name}`

### Authentication System (MongoDB-backed)
- **Persistent User Storage** — MongoDB Atlas (survives server restarts)
- **Email OTP Verification** — Gmail SMTP with 30-minute expiry
- **Forgot Password Flow** — OTP-based password reset
- **Resend OTP** — One-click resend support
- **BCrypt Password Hashing** — Secure password storage
- **JWT-style Session** — localStorage session persistence

### Flexible Data Import
- **Any CSV Format** — Auto schema detection, any number of columns
- **MongoDB Storage** — Persistent dataset storage
- **Dataset Viewer** — Browse imported datasets with full table view
- **Preview Before Import** — See first 5 rows before committing
- **200+ rows tested** — Ebola Sierra Leone dataset (7 columns)

### Analytics Dashboard
- **Real-time Charts** — Chart.js powered visualizations
- **Query Distribution** — Bar chart by operation type
- **Avg Execution Time** — Performance by query type
- **Timeline** — Last 20 queries execution timeline
- **Live Stats** — Total queries, avg exec time, success rate

### ML Anomaly Detection (Python)
- **Isolation Forest** — Unsupervised anomaly detection
- **Risk Scoring** — 0-100 risk score per query
- **Health Score** — Engine health gauge (0-100)
- **4 Visualizations** — Timeline, risk scores, health gauge, type performance
- **JSON Report** — Machine-readable ML analysis output
- **Query Logging** — Every query logged to CSV for ML training

### DevOps & Deployment
- **Multi-stage Docker Build** — Maven + G++ build, Ubuntu runtime
- **Static Linking** — GLIBC compatibility resolved
- **Render.com Deployment** — GitHub-triggered auto-deploy
- **Environment Variables** — Secure credential management
- **MongoDB Atlas** — Free tier cloud database (Mumbai region)

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Storage Engine | C++ 17 | Binary file I/O, B+ Tree indexing |
| API Layer | Java 17 + Spring Boot 3.2 | REST endpoints, IPC bridge |
| Database | MongoDB Atlas | User auth, flexible datasets |
| Build Tool | Maven | Java dependency management |
| Frontend | HTML + CSS + JavaScript | Dashboard, auth, analytics |
| Charts | Chart.js 4.4 | Real-time query performance |
| Email | Spring Mail + Gmail SMTP | OTP delivery |
| ML Analytics | Python + Scikit-learn + Pandas | Anomaly detection |
| Containerization | Docker + Docker Compose | Multi-stage build |
| Hosting | Render.com | Live cloud deployment |

---

## Project Structure

```
4mulaQuery/
├── app/                              # Java Spring Boot Application
│   ├── src/main/java/com/formulaquery/api/
│   │   ├── ApiApplication.java       # Entry point + Bean config
│   │   ├── ApiController.java        # REST endpoints
│   │   ├── EngineService.java        # C++ bridge orchestrator
│   │   ├── ProcessManager.java       # C++ process lifecycle
│   │   ├── StreamHandler.java        # stdin/stdout IPC
│   │   ├── QueryLog.java             # Query log model
│   │   ├── QueryLogger.java          # ML data collector
│   │   ├── CommandType.java          # Enum: INSERT/SEARCH/DELETE/ALL
│   │   ├── User.java                 # MongoDB user entity
│   │   ├── UserRepository.java       # MongoDB user repository
│   │   ├── UserStore.java            # Auth service layer
│   │   ├── FlexibleRecord.java       # MongoDB flexible data entity
│   │   ├── FlexibleRecordRepository.java # MongoDB dataset repository
│   │   └── WebController.java        # Static file routing
│   └── src/main/resources/
│       ├── static/
│       │   ├── index.html            # Single-page application
│       │   ├── app.js                # Frontend logic
│       │   └── style.css             # Dark luxury theme
│       ├── application.properties    # Local config (gitignored)
│       └── application-prod.properties # Render config
├── core/                             # C++ Database Engine
│   ├── main.cpp                      # Command dispatcher
│   ├── btree.h                       # B+ Tree implementation
│   ├── common.h                      # Row schema + constants
│   └── pager.h                       # Page management
├── data/                             # Persistent Data
│   ├── query_logs.csv                # ML training data
│   └── users.json                    # (legacy, replaced by MongoDB)
├── assets/                           # ML Analytics Output
│   └── ml_analytics/
│       ├── 1_anomaly_timeline.png
│       ├── 2_risk_scores.png
│       ├── 3_health_gauge.png
│       └── 4_type_performance.png
├── ml_anomaly.py                     # ML Anomaly Detection script
├── analyze.py                        # Query log analytics
├── Dockerfile                        # Multi-stage build
├── docker-compose.yml                # Container orchestration
└── README.md
```

---

## API Reference

### Database Operations
```bash
# Insert record
GET /api/insert?id=1&name=Abdul&email=abdul@test.com

# Search by ID
GET /api/search?id=1

# Delete by ID
GET /api/delete?id=1

# Get all records
GET /api/all

# Get query logs + analytics
GET /api/logs
```

### Authentication
```bash
# Register
POST /api/auth/register
{"name": "Abdul", "email": "a@test.com", "password": "pass123"}

# Login
POST /api/auth/login
{"email": "a@test.com", "password": "pass123"}

# Forgot password (sends OTP)
POST /api/auth/forgot
{"email": "a@test.com"}

# Reset password
POST /api/auth/reset
{"email": "a@test.com", "otp": "123456", "password": "newpass"}
```

### Data Import
```bash
# Import fixed-schema CSV (id, name, email)
POST /api/import/csv
Content-Type: multipart/form-data
file: <csv-file>

# Import any CSV format → MongoDB
POST /api/import/flexible
Content-Type: multipart/form-data
file: <any-csv-file>

# List all imported datasets
GET /api/datasets

# Get dataset records
GET /api/dataset/{name}
```

---

## Run Locally

### Prerequisites
- Java 17+
- Maven 3.9+
- G++ (C++ 17)
- Python 3.x (for ML analytics)

### Setup

```bash
# Clone repository
git clone https://github.com/4mulaMind/4mulaQuery.git
cd 4mulaQuery

# Compile C++ engine
g++ -O3 -std=c++17 core/main.cpp -o core/4mulaQuery

# Run Java API (from root directory)
mvn -f app/pom.xml spring-boot:run

# Open browser
open http://localhost:8080
```

### ML Analytics (Optional)
```bash
# Install dependencies
pip install pandas scikit-learn matplotlib numpy

# Run anomaly detection
source venv/bin/activate
python3 ml_anomaly.py
```

### Docker
```bash
docker-compose up --build
```

---

## Roadmap

- [x] C++ binary storage engine
- [x] Java Spring Boot REST API
- [x] Docker deployment
- [x] Web UI with auth system
- [x] B+ Tree indexing — O(log n) operations
- [x] OOP refactor (Single Responsibility)
- [x] Python query analytics (analyze.py)
- [x] Analytics Dashboard (Live charts + real-time stats)
- [x] Backend persistent user authentication (MongoDB)
- [x] Email OTP verification + forgot password
- [x] Python ML Anomaly Detection (Isolation Forest)
- [x] ML Risk Scoring per query
- [x] Engine Health Score
- [x] Flexible CSV Import (any schema → MongoDB)
- [x] Dataset Viewer (browse imported datasets)
- [ ] NLP Query Interface ("Show all users from Delhi")
- [ ] Screenshot/Image → CSV (Claude Vision API)
- [ ] SQL Parser (Lexer + AST)
- [ ] Multi-DB Support (PostgreSQL, Cassandra)
- [ ] ML Query Optimizer (predict slow queries)
- [ ] Distributed version

---

## Developer

**Abdul Qadir**
B.Tech AI & ML | DPGITM Gurugram | 2023–2027

- GitHub: [4mulaMind](https://github.com/4mulaMind)
- LinkedIn: [Abdul Qadir](https://linkedin.com/in/abdul-qadir-4aa642204)
- Dev.to: [qadir21](https://dev.to/qadir21)
- Live: [fourmulaquery.onrender.com](https://fourmulaquery.onrender.com)

---

<div align="center">
Built from scratch. Deployed live. Learning never stops. 🚀
</div>
