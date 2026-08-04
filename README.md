<div align="center">

# 🗄️ Distributed Storage System

**A fault-tolerant, distributed file storage engine built with Spring Boot.**  
Files are automatically split into chunks, replicated across nodes, and self-healed when nodes fail.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

</div>

---

## ⚡ How to run it 

```bash
# 1. Clone
git clone https://github.com/Madhan3009/DistributedStorage.git
cd DistributedStorage/demo

# 2. Start PostgreSQL (Docker, fastest way — no install needed)
docker run -d --name pg \
  -e POSTGRES_DB=user_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=admin \
  -p 5432:5432 postgres:16

# 3. Run the coordinator (default port 9000)
./mvnw spring-boot:run

# 4. Open the dashboard
# → http://localhost:9000/dashboard
```

**Done.** Register a user, upload a file, and watch the node health panel.  
No extra config needed — defaults in `application.yml` match the docker run above.

---

## ✨ What This System Does

| Feature | How |
|---|---|
| **Chunked uploads** | Files split into configurable chunks, uploaded independently |
| **Consistent hashing** | `TreeMap`-based virtual-node ring (50 vnodes/node) deterministically places chunks |
| **Replication** | Each chunk copied to N nodes (default: 3). Tolerates `N-1` simultaneous node failures |
| **Three-state health** | `ALIVE → SUSPECTED → DEAD` prevents false positives from GC pauses / network blips |
| **Self-healing** | Background scheduler detects under-replicated chunks and re-replicates them automatically |
| **Fault-tolerant download** | Falls back to alive replicas transparently — callers never see node failures |
| **JWT auth** | Stateless HS512 JWT, 5-hour expiry, Spring Security filter chain |
| **Dashboard** | Browser UI for file uploads, node health monitoring, and failure simulation |

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     Coordinator (port 9000)               │
│                                                          │
│  ┌──────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │FileChunkSvc  │  │NodeRegistrySvc  │  │ConsistentH  │ │
│  │upload/       │  │ALIVE→SUSPECTED  │  │ashRing      │ │
│  │download/     │  │→DEAD lifecycle  │  │TreeMap ring │ │
│  │stream        │  │missedHeartbeats │  │50 vnodes    │ │
│  └──────┬───────┘  └───────┬─────────┘  └──────┬──────┘ │
│         └──────────────────┴───────────────────-┘        │
│                       PostgreSQL                          │
│   file_indices | chunk_placements | storage_nodes        │
│                                                          │
│  Schedulers (background):                                │
│  • HeartbeatScheduler    — marks nodes SUSPECTED/DEAD    │
│  • ReReplicationScheduler — heals under-replicated chunks│
└─────────────────────────┬────────────────────────────────┘
                          │ HTTP /internal/*
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │  Node 1  │    │  Node 2  │    │  Node 3  │
    │  :9001   │    │  :9002   │    │  :9003   │
    │ /store   │    │ /store   │    │ /store   │
    │ /fetch   │    │ /fetch   │    │ /fetch   │
    └──────────┘    └──────────┘    └──────────┘
```

### Upload flow
1. Client POSTs each chunk to `POST /files/chunks`
2. Coordinator hashes `"fileId-chunk-N"` → finds target nodes via the ring
3. Pushes chunk bytes to each node via `POST /internal/store`
4. Saves `ChunkPlacement(fileId, chunkNumber, nodeId, replicaIndex)` to DB
5. When all chunks received → `FileIndex.status = COMPLETE`

### Download flow
1. Client calls `GET /files/download?identifier={fileId}`
2. Coordinator fetches placements for each chunk in order
3. Tries each alive replica until one responds 200 OK
4. Streams bytes directly to client response (no temp disk write)

### Self-healing flow
```
Every 10s: HeartbeatScheduler checks lastHeartbeatAt
  missed 1 window  → SUSPECTED   (no action yet)
  missed 3 windows → DEAD        → triggers ReReplicationScheduler

Every 20s: ReReplicationScheduler (coordinator only)
  1. Find all DEAD node IDs
  2. Query: SELECT DISTINCT fileId WHERE nodeId IN (dead nodes)
  3. For each affected chunk:
     - 0 alive replicas  → mark file CORRUPT, clean DB
     - < replicationFactor alive replicas → fetch from alive node,
       push to new candidate node, save new ChunkPlacement
     - dead placements always deleted from DB
```

---

## 🚀 Setup Guide

### Prerequisites

| Tool | Version |
|---|---|
| Java JDK | 21+ |
| Maven Wrapper | bundled (`./mvnw`) |
| PostgreSQL | 14+ (or Docker) |
| Docker + Compose | For full cluster demo |

### Option A — Run Locally (Single Node)

```bash
git clone https://github.com/Madhan3009/DistributedStorage.git
cd DistributedStorage/demo
```

**Start PostgreSQL via Docker:**
```bash
docker run -d --name pg \
  -e POSTGRES_DB=user_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=admin \
  -p 5432:5432 postgres:16
```

**Run the app (coordinator mode, port 9000):**
```bash
./mvnw spring-boot:run
```

> Hibernate auto-creates all tables on first run (`ddl-auto: update`). No SQL scripts needed.

Dashboard: **http://localhost:9000/dashboard**

---

### Option B — Full 4-Node Docker Cluster

```bash
cd DistributedStorage/demo

# Build the JAR first
./mvnw clean package -DskipTests

# Start coordinator + 3 storage nodes + PostgreSQL
docker compose up --build
```

| Container | Port | Role |
|---|---|---|
| `coordinator` | 9000 | Metadata, upload/download API, re-replication |
| `node-1` | 9001 | Chunk storage |
| `node-2` | 9002 | Chunk storage |
| `node-3` | 9003 | Chunk storage |
| `postgres` | 5432 | Shared metadata DB |

Nodes self-register with the coordinator on startup via `NodeHeartbeatSender`.

---

## 🧪 Run Tests

```bash
cd DistributedStorage/demo
./mvnw test
```

Tests use **H2 in-memory DB** — no PostgreSQL needed.

| Test Class | What It Covers |
|---|---|
| `ConsistentHashRingTest` | Ring distribution, virtual nodes, deduplication |
| `AuthControllerTests` | Register → login → JWT token → access protected endpoint |
| `DistributedStorageIntegrationTest` | Upload → kill node → register candidate → run healer → verify placement → re-download |
| `ReReplicationSchedulerTest` | No dead nodes, under-replication healing, total data loss CORRUPT state |
| `DemoApplicationTests` | Spring context loads successfully |

---

## 🎬 Live Demo Script (for interviews)

> Run through this to show the system live. Takes ~3 minutes.

### Step 1 — Register and Login
```bash
# Register
curl -s -X POST http://localhost:9000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@test.com","password":"demo123"}' | jq .

# Login → copy the token
TOKEN=$(curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}' | jq -r .token)

echo "Token: $TOKEN"
```

### Step 2 — Upload a file (2 chunks)
```bash
# Create a test file
echo "Hello Cisco World - Part 1" > /tmp/part0.txt
echo "Hello Cisco World - Part 2" > /tmp/part1.txt

FILE_ID="cisco-demo-$(date +%s)"

# Upload chunk 0
curl -s -X POST http://localhost:9000/files/chunks \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/part0.txt" \
  -F "chunkNumber=0" \
  -F "totalChunks=2" \
  -F "identifier=$FILE_ID"

# Upload chunk 1
curl -s -X POST http://localhost:9000/files/chunks \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/part1.txt" \
  -F "chunkNumber=1" \
  -F "totalChunks=2" \
  -F "identifier=$FILE_ID"
```

### Step 3 — Download the file back
```bash
curl -s "http://localhost:9000/files/download?identifier=$FILE_ID" \
  -H "Authorization: Bearer $TOKEN" -o /tmp/rebuilt.txt

cat /tmp/rebuilt.txt   # Should show both parts concatenated
```

### Step 4 — Simulate a node failure and observe self-healing
```bash
# Check current node status
curl -s http://localhost:9000/nodes | jq .

# In Docker cluster: stop node-1
docker stop node-1

# Wait ~30-40 seconds for the heartbeat scheduler to mark it DEAD
# Then verify download still works (falls back to node-2/node-3)
curl -s "http://localhost:9000/files/download?identifier=$FILE_ID" \
  -H "Authorization: Bearer $TOKEN" -o /tmp/rebuilt-after-failure.txt

cat /tmp/rebuilt-after-failure.txt   # Still works!

# After ~20 more seconds, re-replication fires automatically
# Check placements moved to healthy nodes:
curl -s http://localhost:9000/nodes | jq .
```

---

## 📡 API Reference

### Authentication

| Method | Endpoint | Body / Params | Description |
|---|---|---|---|
| `POST` | `/auth/register` | `{username, email, password}` | Create account |
| `POST` | `/auth/login` | `{username, password}` | Returns JWT token |

### Files *(require `Authorization: Bearer <token>`)*

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/files/chunks` | Upload one chunk (`file`, `chunkNumber`, `totalChunks`, `identifier`) |
| `GET` | `/files` | List all files for current user |
| `GET` | `/files/download?identifier={id}` | Stream-download a file |

### Nodes *(public)*

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/nodes` | List all nodes with status and disk metrics |
| `POST` | `/nodes/register?nodeId=&host=&port=` | Register a node |
| `POST` | `/nodes/heartbeat?nodeId=&diskUsed=&diskFree=` | Update heartbeat + disk stats |

### Observability

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Liveness check |
| `GET /actuator/metrics` | JVM + custom `replication.healed.chunks` / `replication.failed.chunks` |

---

## ⚙️ Configuration Reference

All overridable via environment variables:

| Variable | Default | Description |
|---|---|---|
| `PORT` | `9000` | HTTP server port |
| `DB_HOST` | `postgres` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `user_db` | Database name |
| `DB_USERNAME` | `postgres` | DB username |
| `DB_PASSWORD` | `admin` | DB password |
| `APP_ROLE` | `COORDINATOR` | `COORDINATOR` or `NODE` |
| `APP_REPLICATION_FACTOR` | `3` | Replicas per chunk |
| `APP_MAX_CHUNKS` | `10` | Max chunks per file |
| `APP_COORDINATOR_URL` | `http://localhost:9000` | Coordinator URL (NODE role only) |
| `APP_NODE_ID` | `coordinator` | Unique node identifier |
| `APP_HOST` | `localhost` | Host this node is reachable on |
| `APP_TEMP_DIR` | `temp/` | Local staging directory |
| `APP_UPLOAD_DIR` | `uploads/` | Assembled file directory |

---

## 🗂️ Project Structure

```
demo/src/main/java/com/dSystems/demo/
├── Config/
│   ├── AppConfig.java              # Spring Security filter chain + RestTemplate bean
│   └── StorageProperties.java      # @ConfigurationProperties binding
├── Controller/
│   ├── AuthController.java         # Register / Login
│   ├── FileChunkController.java    # Upload / Download / List
│   ├── NodeController.java         # Node registry admin
│   ├── InternalNodeController.java # /internal/store, /fetch, /delete (node-to-node)
│   └── DashboardController.java    # Serves the web UI
├── Model/
│   ├── FileIndex.java              # File metadata (PENDING→COMPLETE→CORRUPT)
│   ├── ChunkPlacement.java         # Replica location records
│   ├── StorageNode.java            # Node with missedHeartbeats counter
│   └── AppUser.java                # User account entity
├── Repository/                     # Spring Data JPA interfaces
├── Scheduler/
│   ├── HeartbeatScheduler.java     # Polls lastHeartbeatAt → SUSPECTED/DEAD transitions
│   ├── ReReplicationScheduler.java # Self-healing: fetch → push → update placements
│   └── NodeHeartbeatSender.java    # NODE role: sends /register + /heartbeat every 10s
├── Security/
│   ├── JWTHelper.java              # Token generation & validation (HS512)
│   ├── JWTAuthenticationFilter.java# Extracts Bearer token, populates SecurityContext
│   └── CustomUserDetailsService.java
└── Service/
    ├── ConsistentHashRing.java     # TreeMap-based ring, 50 virtual nodes per physical node
    ├── FileChunkService.java       # Core upload / download / streaming logic
    └── NodeRegistryService.java    # Node lifecycle: register, heartbeat, handleMissedHeartbeat
```

---

## 💡 Key Design Decisions (Interview Talking Points)

| Decision | Why |
|---|---|
| **Coordinator-Worker** | Centralized metadata = no distributed consensus needed for placement |
| **Consistent Hashing** | Adding/removing nodes only remaps O(K/N) keys, not all keys |
| **50 virtual nodes** | Prevents hot spots when physical node count is small |
| **ALIVE→SUSPECTED→DEAD** | 3 missed heartbeats before DEAD avoids false positives from GC pauses |
| **`AtomicBoolean` run lock** | Non-blocking CAS prevents overlapping healer runs without thread blocking |
| **`@ConditionalOnProperty`** | Ensures re-replication only runs on coordinator — prevents race conditions in multi-node deploy |
| **Stream-on-download** | Chunks piped directly to response `OutputStream` — memory usage bounded by chunk size, not file size |
| **H2 for tests** | Full JPA integration with zero infrastructure dependency |
| **`CORRUPT` status** | Explicit signalling when total data loss is detected; prevents silent failures |

---

## 🛡️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Security | Spring Security + JJWT (HS512) |
| Persistence | Spring Data JPA + Hibernate |
| Database (runtime) | PostgreSQL 14+ |
| Database (tests) | H2 in-memory |
| Node communication | HTTP REST (`RestTemplate`) |
| Observability | Spring Boot Actuator + Micrometer |
| Containerization | Docker + Docker Compose |

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
