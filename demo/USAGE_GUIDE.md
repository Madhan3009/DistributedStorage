# Distributed Storage System — Usage Guide

A practical reference covering everything you can do with this system, with the exact flow for each action.

---

## Table of Contents

0. [Running the Application](#0-running-the-application)
1. [Authentication](#1-authentication)
2. [Upload a File](#2-upload-a-file)
3. [Download a File](#3-download-a-file)
4. [List Your Files](#4-list-your-files)
5. [Node Registry & Health](#5-node-registry--health)
6. [Simulate a Node Failure](#6-simulate-a-node-failure)
7. [Self-Healing / Re-Replication](#7-self-healing--re-replication)
8. [Admin Dashboard Console](#8-admin-dashboard-console)
9. [Run the Full Cluster with Docker](#9-run-the-full-cluster-with-docker)
10. [Monitoring & Health Checks](#10-monitoring--health-checks)
11. [End-to-End Flow Diagram](#11-end-to-end-flow-diagram)

---

## 0. Running the Application

> **Important:** All commands must be run from inside the `demo/` subdirectory, not the repository root.
> The Maven wrapper (`mvnw`) and `docker-compose.yml` both live there.

### Directory structure reminder

```
DistributedStorage/          ← repository root (do NOT run commands here)
└── demo/                    ← run all commands from here
    ├── mvnw
    ├── pom.xml
    ├── docker-compose.yml
    └── src/
```

---

### Option A — Run locally (single node, no Docker)

**Prerequisites:**
- Java 21 installed
- PostgreSQL running (locally or accessible)

**Step 1 — Navigate to the correct directory**

```bash
cd /home/madhan/Desktop/DistributedStorage/demo
```

**Step 2 — Set database environment variables**

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=user_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
```

**Step 3 — Start the application**

```bash
./mvnw spring-boot:run
```

The server starts on **port 9000**. You will see log output like:

```
Tomcat started on port 9000
Started DemoApplication in X seconds
```

**Step 4 — Open the dashboard**

```
http://localhost:9000/dashboard
```

In this mode, the application acts as the **COORDINATOR** (default role).  
You can manually register storage nodes against it using `POST /nodes/register`.

---

### Option B — Run with Docker (full multi-node cluster)

**Prerequisites:**
- Docker and Docker Compose installed
- No local PostgreSQL needed — Compose brings its own

**Step 1 — Navigate to the correct directory**

```bash
cd /home/madhan/Desktop/DistributedStorage/demo
```

**Step 2 — Package the app and start all containers**

```bash
# Package the application JAR file first
./mvnw clean package -DskipTests

# Build and start the cluster
docker compose up --build
```

This starts 5 containers:

| Container | Port | Role |
|---|---|---|
| `postgres` | 5432 | Database |
| `coordinator` | 9000 | Metadata + file API |
| `node-1` | 9001 | Chunk storage |
| `node-2` | 9002 | Chunk storage |
| `node-3` | 9003 | Chunk storage |

Storage nodes auto-register themselves with the coordinator on startup — no manual steps needed.

**Step 3 — Open the dashboard**

```
http://localhost:9000/dashboard
```

**To stop all containers:**

```bash
docker compose down
```

---

### Option C — Run tests only

Tests use an **in-memory H2 database** and do not require PostgreSQL to be running.

```bash
cd /home/madhan/Desktop/DistributedStorage/demo

./mvnw test
```

You should see output like:

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

### Common mistakes

| Mistake | Fix |
|---|---|
| `bash: ./mvnw: No such file or directory` | You are in the repo root. Run `cd demo` first, then retry. |
| `spring-boot:run: command not found` | Use `./mvnw spring-boot:run`, not just `spring-boot:run` |
| App starts but database fails | Check your `DB_*` env vars are exported in the same terminal session |
| Port 9000 already in use | Kill the existing process: `lsof -ti:9000 \| xargs kill` |

---



## 1. Authentication

Before doing anything with files, you need a user account and a JWT token.

### Register an account

```bash
curl -X POST http://localhost:9000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "secret123"
  }'
```

**What happens internally:**
1. Request body is validated
2. Password is hashed with BCrypt
3. User record is saved to PostgreSQL
4. `201 Created` is returned

---

### Login

```bash
curl -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "secret123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "alice"
}
```

**What happens internally:**
1. Spring Security authenticates the credentials against the database
2. A JWT token is signed with a 5-hour expiry
3. All subsequent requests must include `Authorization: Bearer <token>`

---

### Verify your token

```bash
curl http://localhost:9000/api/me \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Returns your username if the token is valid.

---

## 2. Upload a File

Files are uploaded as individual chunks. The client (or the dashboard) is responsible for slicing the file.

### Flow

```
Client                        Coordinator                    Storage Nodes
  │                               │                               │
  │── POST /files/chunks ────────▶│                               │
  │   (chunk 0, of 3)             │                               │
  │                               │── compute target nodes ──────▶│
  │                               │   (consistent hash ring)      │
  │                               │── POST /internal/store ──────▶│ Node-1
  │                               │── POST /internal/store ──────▶│ Node-2  (replica)
  │                               │── POST /internal/store ──────▶│ Node-3  (replica)
  │                               │── save ChunkPlacement ────────│
  │◀── 200 OK ───────────────────│                               │
  │                               │                               │
  │── POST /files/chunks ────────▶│  (chunk 1, of 3)              │
  │── POST /files/chunks ────────▶│  (chunk 2, of 3)              │
  │                               │                               │
  │                               │── FileIndex status = COMPLETE │
  │◀── 200 Completed ────────────│                               │
```

### Upload chunk by chunk (curl)

```bash
# Split a file into 3 pieces first
split -b 512k myfile.zip chunk_

# Upload each chunk
curl -X POST http://localhost:9000/files/chunks \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@chunk_aa" \
  -F "chunkNumber=0" \
  -F "totalChunks=3" \
  -F "identifier=my-file-001"

curl -X POST http://localhost:9000/files/chunks \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@chunk_ab" \
  -F "chunkNumber=1" \
  -F "totalChunks=3" \
  -F "identifier=my-file-001"

curl -X POST http://localhost:9000/files/chunks \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@chunk_ac" \
  -F "chunkNumber=2" \
  -F "totalChunks=3" \
  -F "identifier=my-file-001"
```

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `file` | multipart | The raw bytes of this chunk |
| `chunkNumber` | int | Zero-indexed chunk position |
| `totalChunks` | int | Total number of chunks in this file |
| `identifier` | string | A unique ID that groups all chunks of one file |

**What happens internally:**
1. Each chunk is received by the coordinator
2. The consistent hash ring selects `replicationFactor` target nodes for the chunk
3. The chunk is forwarded via HTTP to each node's `/internal/store` endpoint
4. A `ChunkPlacement` record is written to the database for every replica
5. When the last chunk arrives, the `FileIndex` status is set to `COMPLETE`

---

## 3. Download a File

```bash
curl -o recovered_file.zip \
  "http://localhost:9000/files/download?identifier=my-file-001" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Flow

```
Client                        Coordinator                    Storage Nodes
  │                               │                               │
  │── GET /files/download ───────▶│                               │
  │                               │── load FileIndex from DB ─────│
  │                               │── for each chunk:             │
  │                               │     load ChunkPlacements      │
  │                               │     try Node-1:               │
  │                               │       GET /internal/fetch ───▶│ Node-1
  │                               │◀──────── chunk bytes ─────────│
  │                               │     stream chunk to client    │
  │◀── streaming bytes ──────────│                               │
  │       (chunk 0)               │                               │
  │◀── streaming bytes ──────────│                               │
  │       (chunk 1)               │                               │
  │◀── streaming bytes ──────────│                               │
  │       (chunk 2)               │                               │
```

**What happens internally:**
1. The coordinator loads the `FileIndex` and verifies ownership
2. For each chunk (in order), it reads all `ChunkPlacement` records from the database
3. It contacts each node replica in sequence until one responds successfully
4. Bytes are written directly to the HTTP response stream — no temporary file is created on the coordinator
5. The client receives a complete file via standard HTTP streaming

---

## 4. List Your Files

```bash
curl http://localhost:9000/files \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Response:**
```json
[
  {
    "fileId": "my-file-001",
    "fileName": "myfile.zip",
    "fileSize": 1572864,
    "totalChunks": 3,
    "status": "COMPLETE",
    "ownerUsername": "alice"
  }
]
```

Only files owned by the authenticated user are returned.

---

## 5. Node Registry & Health

Storage nodes register themselves with the coordinator. You can inspect and manage them.

### View all registered nodes

```bash
curl http://localhost:9000/nodes
```

**Response:**
```json
[
  {
    "nodeId": "node-1",
    "host": "localhost",
    "port": 9001,
    "status": "ALIVE",
    "diskUsedBytes": 2097152,
    "diskFreeBytes": 104857600,
    "lastHeartbeatAt": "2026-07-07T19:50:00"
  },
  {
    "nodeId": "node-2",
    "host": "localhost",
    "port": 9002,
    "status": "ALIVE",
    "diskUsedBytes": 1048576,
    "diskFreeBytes": 104857600,
    "lastHeartbeatAt": "2026-07-07T19:50:03"
  }
]
```

### Manually register a node

```bash
curl -X POST "http://localhost:9000/nodes/register?nodeId=node-4&host=localhost&port=9004"
```

### Send a heartbeat from a node

```bash
curl -X POST "http://localhost:9000/nodes/heartbeat?nodeId=node-1&diskUsed=2097152&diskFree=104857600"
```

**What happens internally:**
- Nodes send a heartbeat every **10 seconds** automatically (when running in `NODE` role)
- The coordinator checks node freshness every **30 seconds** via `HeartbeatScheduler`
- Any node that has not sent a heartbeat in over 30 seconds is marked `DEAD`

---

## 6. Simulate a Node Failure

Mark a node as dead to test fault-tolerance behaviour:

```bash
curl -X POST http://localhost:9000/nodes/node-1/fail
```

### What happens after a node is killed

```
HeartbeatScheduler (every 30s)
  │
  │── detects node-1 lastHeartbeatAt > 30s ago
  │── marks node-1 status = DEAD
  │
ReReplicationScheduler (every 20s)
  │
  │── scans ChunkPlacements for chunks on DEAD nodes
  │── for each under-replicated chunk:
  │     fetch chunk bytes from an alive replica (node-2 or node-3)
  │     push chunk to a new candidate alive node
  │     create new ChunkPlacement record
  │     delete old ChunkPlacement record for dead node
  │── replica count is restored to replicationFactor
```

Downloads continue to work during this period because the coordinator automatically skips dead nodes and falls back to alive replicas.

---

## 7. Self-Healing / Re-Replication

The `ReReplicationScheduler` runs **every 20 seconds** in the background with no manual intervention needed.

### Trigger conditions

- A node is marked `DEAD`
- The number of alive replicas for a chunk falls below `replicationFactor`

### What it does

1. Scans all `ChunkPlacement` records for chunks sitting on dead nodes
2. For each under-replicated chunk, selects a source node (any alive replica)
3. Fetches the chunk bytes from the source via `GET /internal/fetch`
4. Selects a new target node that does not already hold a replica
5. Pushes the chunk to the new node via `POST /internal/store`
6. Saves a new `ChunkPlacement` record for the new node
7. Removes the stale `ChunkPlacement` record for the dead node

---

## 8. Admin Dashboard Console

Open the browser dashboard at:

```
http://localhost:9000/dashboard
```

No separate frontend server is needed — the dashboard is served as a static file by the same Spring Boot application.

### What you can do in the dashboard

| Feature | Description |
|---|---|
| **Login / Register** | JWT-based auth form, token stored in `localStorage` |
| **Node health panel** | Live list of all nodes with ALIVE/DEAD status, disk usage bar, last heartbeat time |
| **Kill Node button** | Instantly marks any node as dead to observe self-healing |
| **File index table** | Lists all your files with size, chunk count, and status |
| **Drag & drop upload** | Drop any file onto the upload zone — it is automatically sliced into 50 KB chunks and uploaded in sequence |
| **Upload progress bar** | Shows per-chunk progress as the file is being distributed |
| **Download / Retrieve** | Reassembles the file from alive node replicas and triggers a browser download |

### Dashboard login flow

```
Open /dashboard
  │
  ├── No token in localStorage?
  │     Show Login / Register modal
  │     POST /auth/login
  │     Store token
  │
  └── Token found?
        Load node topology (GET /nodes)
        Load file index (GET /files)
        Start auto-refresh every 5s
```

---

## 9. Run the Full Cluster with Docker

```bash
cd demo/
docker compose up --build
```

This creates:

```
postgres (5432) ── shared metadata DB
      │
coordinator (9000) ── handles all user requests, routes chunks
      ├── node-1 (9001) ── stores chunk replicas
      ├── node-2 (9002) ── stores chunk replicas
      └── node-3 (9003) ── stores chunk replicas
```

### Node startup sequence

```
node-1 container starts
  │
  │── NodeHeartbeatSender initialises
  │── POST http://coordinator:9000/nodes/register
  │       ?nodeId=node-1&host=node-1&port=9001
  │── coordinator saves node to DB, status = ALIVE
  │
  │── repeat every 10s:
  │     POST http://coordinator:9000/nodes/heartbeat
  │           ?nodeId=node-1&diskUsed=...&diskFree=...
```

### Stopping a container to test real failure

```bash
# Stop node-2 to simulate a real container crash
docker compose stop node-2

# Within ~30 seconds the coordinator marks it DEAD
# Within ~20 seconds after that, re-replication runs and heals affected chunks

# Bring it back up
docker compose start node-2
# node-2 re-registers itself automatically on startup
```

---

## 10. Monitoring & Health Checks

### Application health

```bash
curl http://localhost:9000/actuator/health
```

```json
{
  "status": "UP"
}
```

### JVM and application metrics

```bash
curl http://localhost:9000/actuator/metrics
```

Lists all available metric names. Drill into a specific one:

```bash
curl http://localhost:9000/actuator/metrics/jvm.memory.used
curl http://localhost:9000/actuator/metrics/http.server.requests
```

---

## 11. End-to-End Flow Diagram

```
User Action: Upload "report.pdf" (1.5 MB, split into 3 × 512 KB chunks)

┌──────────────────────────────────────────────────────────────────┐
│ Chunk 0 upload                                                   │
│  client → POST /files/chunks (chunkNumber=0, totalChunks=3)     │
│  coordinator → hash("file-id:0") → select node-1, node-2, node-3 │
│  coordinator → POST /internal/store → node-1 ✓                  │
│  coordinator → POST /internal/store → node-2 ✓                  │
│  coordinator → POST /internal/store → node-3 ✓                  │
│  DB: ChunkPlacement(chunk=0, node=node-1, replica=0)            │
│  DB: ChunkPlacement(chunk=0, node=node-2, replica=1)            │
│  DB: ChunkPlacement(chunk=0, node=node-3, replica=2)            │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ Chunk 1, 2 follow the same process                               │
│  FileIndex status → COMPLETE after chunk 2                       │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ node-2 crashes                                                   │
│  HeartbeatScheduler → no heartbeat from node-2 in 30s           │
│  node-2 marked DEAD                                             │
│  ReReplicationScheduler → chunks 0,1,2 now have only 2 replicas │
│  → fetch chunk-0 from node-1                                    │
│  → push to node-4 (new alive node)                              │
│  → update ChunkPlacement table                                  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ User downloads "report.pdf"                                      │
│  client → GET /files/download?identifier=file-id                │
│  coordinator → load FileIndex (totalChunks=3)                   │
│  chunk 0: try node-1 → success → stream to client               │
│  chunk 1: try node-2 → DEAD, skip → try node-1 → success       │
│  chunk 2: try node-3 → success → stream to client               │
│  client receives complete file                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Quick Reference

| What you want to do | How |
|---|---|
| Register | `POST /auth/register` |
| Login | `POST /auth/login` → save the `token` |
| Upload a file | Slice it, then `POST /files/chunks` per chunk |
| Download a file | `GET /files/download?identifier=...` |
| List your files | `GET /files` |
| See all nodes | `GET /nodes` |
| Kill a node | `POST /nodes/{nodeId}/fail` |
| Check system health | `GET /actuator/health` |
| Open dashboard | `http://localhost:9000/dashboard` |
| Start full cluster | `docker compose up --build` in `demo/` |
