# Distributed Storage System — Progress Tracker

## Project Goal
Simulate a distributed storage system with multiple nodes, consistent hashing, file replication, fault detection, and an admin dashboard — built on the existing Spring Boot 4 / Java 21 backend.

---

## Current State (Baseline — 2026-07-07)

### ✅ Already Done
- [x] Spring Boot 4 project scaffolded (Maven wrapper, Docker Compose)
- [x] JWT authentication (register, login, protected routes)
- [x] PostgreSQL as runtime DB, H2 for isolated tests
- [x] Chunked file upload (3 chunks per file, flat-file metadata)
- [x] File rebuild from chunks endpoint (`POST /files/rebuild`)
- [x] Global exception handler
- [x] Integration test: auth register → login → protected access
- [x] Docker Compose with single app + postgres

### ❌ Not Yet Started
- [ ] Node registry (multiple storage nodes)
- [ ] Consistent hashing ring
- [ ] Chunk distribution across nodes
- [ ] Replication factor (N replicas per chunk)
- [ ] Heartbeat / health detection scheduler
- [ ] Re-replication on node failure
- [ ] File download with fault-tolerant routing
- [ ] Admin dashboard (node health, file browser, upload UI)
- [ ] Multi-node Docker Compose (coordinator + 3 storage nodes)
- [ ] Tests for consistent hash ring and file distribution

---

## Phase Progress

| Phase | Status | Description |
|---|---|---|
| Phase 1 | ✅ Completed | Refactor core: flexible chunk limit, FileIndex entity |
| Phase 2 | ✅ Completed | Node registry, heartbeat scheduler |
| Phase 3 | ✅ Completed | Consistent hashing, chunk distribution |
| Phase 4 | ✅ Completed | File download, fault-tolerant fetch, re-replication |
| Phase 5 | ✅ Completed | Docker multi-node Compose |
| Phase 6 | ✅ Completed | Admin dashboard (HTML/JS) |
| Phase 7 | ✅ Completed | Tests, Actuator / metrics |

---

## Phase Details

### Phase 1 — Refactor Core & FileIndex
**Goal**: Remove hardcoded 3-chunk limit and add a DB-tracked file index.

- [x] Make `maxChunksPerFile` a true upper-bound (not exact-match)
- [x] Add `FileIndex` JPA entity (`fileId`, `fileName`, `status`, `ownerId`, `uploadedAt`)
- [x] Add `FileIndexRepository`
- [x] Update `FileChunkService` to persist file index on upload completion
- [x] Add `listFiles()` endpoint

---

### Phase 2 — Node Registry & Heartbeat
**Goal**: The coordinator knows which nodes are alive and can route traffic.

- [x] Add `StorageNode` JPA entity (`nodeId`, `host`, `port`, `status`, `lastHeartbeatAt`, `diskUsedBytes`)
- [x] Add `StorageNodeRepository`
- [x] Implement `NodeRegistryService` (`register`, `heartbeat`, `markDead`, `getAliveNodes`)
- [x] Add `NodeController` (`POST /nodes/register`, `POST /nodes/{id}/heartbeat`, `GET /nodes`)
- [x] Add `HeartbeatScheduler` (`@Scheduled`) — pings each node's `/health` every 10s; marks dead after 3 failures

---

### Phase 3 — Consistent Hashing & Distribution
**Goal**: Files/chunks are spread across nodes deterministically and replicated.

- [x] Implement `ConsistentHashRing` with virtual nodes (TreeMap-based)
- [x] Add `ChunkPlacement` JPA entity (`chunkId`, `fileId`, `chunkNumber`, `nodeId`, `replicaIndex`)
- [x] Add `ChunkPlacementRepository`
- [x] Refactor `FileChunkService.uploadChunk()` to forward chunks to target nodes via HTTP
- [x] Add `InternalNodeController` (`/internal/store`, `/internal/fetch`, `/health`)
- [x] Write unit tests for `ConsistentHashRing`

---

### Phase 4 — Download & Fault Tolerance
**Goal**: Files can be downloaded; system survives node failures gracefully.

- [x] Implement `FileDownloadService` (fetch chunks from alive nodes, stream merged file)
- [x] Add `FileController` (`GET /files/{fileId}`, `GET /files`, `DELETE /files/{fileId}`)
- [x] Implement `ReReplicationScheduler` — finds under-replicated chunks, re-copies them
- [x] Integration test: upload → kill node → verify file still downloadable

---

### Phase 5 — Multi-Node Docker Compose
**Goal**: Run coordinator + 3 storage nodes as separate containers.

- [x] Update `docker-compose.yml` with `coordinator`, `node-1`, `node-2`, `node-3` services
- [x] Add `ROLE` env var handling in `application.yml` (COORDINATOR vs NODE profile)
- [x] Nodes auto-register with coordinator on startup (`ApplicationRunner`)
- [x] Verify end-to-end with `docker compose up --build`

---

### Phase 6 — Admin Dashboard
**Goal**: A visual UI to observe the cluster, upload files, and simulate failures.

- [x] Node health panel (green/red per node, disk usage, last heartbeat)
- [x] File index table (filename, size, chunks, replicas, status)
- [x] Drag-and-drop upload widget
- [x] File download browser
- [x] "Simulate Node Failure" button (`POST /nodes/{id}/fail`)
- [x] Auto-refresh every 5s

---

### Phase 7 — Tests & Observability
**Goal**: Confidence in correctness + production-ready metrics.

- [x] `ConsistentHashRingTest` — distribution uniformity, replica count, re-routing
- [x] `FileDistributionIntegrationTest` — upload/kill node/verify re-replication/download
- [x] Add `spring-boot-starter-actuator` to pom.xml
- [x] Add Prometheus exporter (`micrometer-registry-prometheus`)
- [x] Verify `/actuator/health` and `/actuator/metrics`

---

## Milestone Summary

| Milestone | Target | Status |
|---|---|---|
| M1: Working node registry + heartbeat | Phase 2 complete | ✅ |
| M2: Files distributed across 3 nodes | Phase 3 complete | ✅ |
| M3: Fault-tolerant download + re-replication | Phase 4 complete | ✅ |
| M4: Full Docker cluster running | Phase 5 complete | ✅ |
| M5: Dashboard live | Phase 6 complete | ✅ |
| M6: All tests green | Phase 7 complete | ✅ |

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Build | Maven (wrapper included) |
| Database | PostgreSQL (runtime), H2 (tests) |
| Auth | JWT (JJWT 0.12.6) |
| Containerization | Docker + Docker Compose |
| Dashboard | HTML5 + Vanilla JS (served via Spring Boot static) |
| Metrics | Micrometer + Actuator |

---

## Key Design Decisions

| Decision | Choice | Reason |
|---|---|---|
| Hash ring | Consistent hashing (TreeMap + virtual nodes) | Minimizes re-assignment when nodes join/leave |
| Replication | 3 replicas per chunk (configurable) | Industry default; tolerates 2 simultaneous failures |
| Node simulation | Separate Docker containers (one Spring Boot JAR, ROLE env var) | Realistic simulation without multiple codebases |
| Metadata store | PostgreSQL (`ChunkPlacement` table) | Survives restarts; already available in the stack |
| Dashboard | Embedded static HTML/JS | Zero extra build step; served by Spring Boot |

---

## Notes & Gotchas

- `maxChunksPerFile=3` is currently an **exact-match** constraint (not a maximum). This **must be fixed** in Phase 1 before any meaningful distribution work.
- `StorageProperties` uses `@ConfigurationProperties(prefix = "app")` — the JWT secret is under the same `app.*` prefix. Keep naming consistent when adding new properties.
- Tests currently rely on `@ActiveProfiles("test")` + `application-test.properties` using H2. New integration tests for distributed behavior will need either TestContainers (PostgreSQL) or the existing H2 setup extended.
- The `@EnableAsync` annotation on `FileChunkController` is currently unused — async replication in Phase 3 should hook into this.
