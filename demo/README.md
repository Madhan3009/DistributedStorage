<div align="center">

# 🗄️ Distributed Storage System

**A fault-tolerant, distributed file storage engine built with Spring Boot.**  
Files are automatically split into chunks, replicated across nodes, and reassembled on demand — even when nodes fail.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

</div>

---

## ✨ Overview

This project simulates a **production-grade distributed file storage system** with the following capabilities:

- **Chunked uploads** — files are sliced into configurable chunks and distributed independently
- **Consistent hashing** — chunks are deterministically placed across nodes using a virtual-node ring
- **Replication** — each chunk is replicated to `N` nodes (configurable, default: 3)
- **Self-healing** — a background scheduler detects under-replicated chunks and automatically re-replicates them when a node goes down
- **Fault-tolerant retrieval** — downloads fall back to alive replicas if the primary node is unreachable
- **JWT authentication** — all user-facing endpoints are protected with stateless JWT tokens
- **Admin dashboard** — a browser-based console for uploading files, monitoring node health, and simulating node failures

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Coordinator Node                  │
│                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │
│  │ FileChunk   │  │  Node       │  │ Consistent │  │
│  │ Service     │  │  Registry   │  │ Hash Ring  │  │
│  │ (upload/    │  │  Service    │  │ (chunk     │  │
│  │  download)  │  │  (health)   │  │  routing)  │  │
│  └──────┬──────┘  └──────┬──────┘  └─────┬──────┘  │
│         └───────────────┬┴────────────────┘         │
│                    PostgreSQL DB                     │
└─────────────────────────┬───────────────────────────┘
                          │ HTTP (internal)
        ┌─────────────────┼──────────────────┐
        ▼                 ▼                  ▼
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │  Node 1  │      │  Node 2  │      │  Node 3  │
  │ /internal│      │ /internal│      │ /internal│
  │  store   │      │  store   │      │  store   │
  │  fetch   │      │  fetch   │      │  fetch   │
  └──────────┘      └──────────┘      └──────────┘
```

### How a file upload works

1. Client slices the file into chunks and uploads each one to `POST /files/chunks`
2. The coordinator computes target nodes for each chunk using the **consistent hash ring**
3. Each chunk is forwarded to `N` storage nodes via `POST /internal/store` (replication)
4. Placement records are saved to the database
5. When all chunks arrive, the file index is marked `COMPLETE`

### How a download works

1. Client calls `GET /files/download?identifier={fileId}`
2. Coordinator looks up each chunk's placement records from the database
3. For each chunk, it tries alive replicas in order until it gets a successful response
4. Chunk bytes are streamed directly to the client response — no temp merge file needed

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 21+ |
| Maven Wrapper | Bundled (`./mvnw`) |
| PostgreSQL | 14+ |
| Docker + Compose | Optional (for cluster mode) |

### 1. Clone the repository

```bash
git clone https://github.com/your-username/DistributedStorage.git
cd DistributedStorage/demo
```

### 2. Configure the database

Set these environment variables before running:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=user_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
```

### 3. Run locally (single node — coordinator mode)

```bash
./mvnw spring-boot:run
```

The application starts on **port 9000**. Navigate to `http://localhost:9000/dashboard` to open the admin console.

---

## 🐳 Running a Multi-Node Cluster with Docker

This spins up **1 coordinator + 3 storage nodes + PostgreSQL** as separate containers.

```bash
# From the demo/ directory
# 1. Package the application JAR
./mvnw clean package -DskipTests

# 2. Build and start the containers
docker compose up --build
```

| Service | Port | Role |
|---|---|---|
| `coordinator` | 9000 | Metadata authority, hash ring, file API |
| `node-1` | 9001 | Chunk storage |
| `node-2` | 9002 | Chunk storage |
| `node-3` | 9003 | Chunk storage |
| `postgres` | 5432 | Shared metadata database |

Storage nodes auto-register with the coordinator on startup and send heartbeats every 10 seconds.

---

## 📡 API Reference

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/auth/register` | Register a new user |
| `POST` | `/auth/login` | Login and receive a JWT token |
| `GET` | `/api/me` | Returns the authenticated username |

#### Register

```bash
curl -X POST http://localhost:9000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123"}'
```

#### Login

```bash
curl -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}'
# Returns: {"token":"eyJ...", "username":"alice"}
```

### File Operations

All file endpoints require `Authorization: Bearer <token>`.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/files/chunks` | Upload one chunk of a file |
| `GET` | `/files` | List all files owned by the current user |
| `GET` | `/files/download?identifier={id}` | Download and reassemble a file |

#### Upload a file chunk

```bash
curl -X POST http://localhost:9000/files/chunks \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@/path/to/chunk.part" \
  -F "chunkNumber=0" \
  -F "totalChunks=3" \
  -F "identifier=my-unique-file-id"
```

#### Download a file

```bash
curl -o output_file.bin \
  "http://localhost:9000/files/download?identifier=my-unique-file-id" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Node Management

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/nodes/register` | Register a storage node |
| `POST` | `/nodes/heartbeat` | Send a node heartbeat |
| `GET` | `/nodes` | List all registered nodes |
| `POST` | `/nodes/{nodeId}/fail` | Mark a node as dead (simulate failure) |

### Observability

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Application health check |
| `GET /actuator/metrics` | JVM and application metrics |

---

## ⚙️ Configuration Reference

All properties can be overridden via environment variables:

| Environment Variable | Default | Description |
|---|---|---|
| `PORT` | `9000` | HTTP server port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `user_db` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | *(required)* | Database password |
| `APP_ROLE` | `COORDINATOR` | `COORDINATOR` or `NODE` |
| `APP_REPLICATION_FACTOR` | `3` | Number of replicas per chunk |
| `APP_MAX_CHUNKS` | `10` | Max chunks per file |
| `APP_COORDINATOR_URL` | `http://localhost:9000` | Coordinator base URL (NODE role only) |
| `APP_NODE_ID` | `coordinator` | Unique identifier for this node |
| `APP_HOST` | `localhost` | Host this node is reachable on |

---

## 🧪 Testing

Tests run against an isolated **H2 in-memory database** and do not touch your PostgreSQL instance.

```bash
./mvnw test
```

### Test coverage

| Test Class | What it tests |
|---|---|
| `ConsistentHashRingTest` | Hash ring distribution, virtual nodes, replica selection |
| `AuthControllerTests` | Register → login → access protected endpoint |
| `DistributedStorageIntegrationTest` | End-to-end: upload chunks → kill node → download via failover |
| `DemoApplicationTests` | Spring context loads cleanly |

---

## 🗂️ Project Structure

```
src/
├── main/
│   ├── java/com/dSystems/demo/
│   │   ├── Config/
│   │   │   ├── AppConfig.java            # Security filter chain
│   │   │   └── StorageProperties.java    # @ConfigurationProperties
│   │   ├── Controller/
│   │   │   ├── AuthController.java       # Register / Login
│   │   │   ├── FileChunkController.java  # Upload / Download / List
│   │   │   ├── NodeController.java       # Node registry admin
│   │   │   ├── InternalNodeController.java # Peer-to-peer chunk I/O
│   │   │   └── DashboardController.java  # Redirect to dashboard UI
│   │   ├── Model/
│   │   │   ├── FileIndex.java            # File metadata entity
│   │   │   ├── ChunkPlacement.java       # Replica location entity
│   │   │   └── StorageNode.java          # Node registration entity
│   │   ├── Repository/                   # Spring Data JPA repositories
│   │   ├── Scheduler/
│   │   │   ├── HeartbeatScheduler.java   # Prune dead nodes
│   │   │   ├── ReReplicationScheduler.java # Heal under-replicated chunks
│   │   │   └── NodeHeartbeatSender.java  # Self-register when in NODE role
│   │   ├── Security/                     # JWT filter & helper
│   │   └── Service/
│   │       ├── ConsistentHashRing.java   # TreeMap-based virtual node ring
│   │       ├── FileChunkService.java     # Upload, download, replication logic
│   │       └── NodeRegistryService.java  # Node lifecycle management
│   └── resources/
│       ├── application.yml
│       └── static/dashboard/index.html   # Admin console UI
└── test/
    └── java/com/dSystems/demo/
        ├── ConsistentHashRingTest.java
        ├── AuthControllerTests.java
        └── DistributedStorageIntegrationTest.java
```

---

## 🛡️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Security | Spring Security + JJWT |
| Persistence | Spring Data JPA + Hibernate |
| Database (runtime) | PostgreSQL |
| Database (tests) | H2 (in-memory) |
| Cluster communication | HTTP (RestTemplate) |
| Observability | Spring Boot Actuator |
| Containerization | Docker + Docker Compose |

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
