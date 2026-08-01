package com.dSystems.demo;

import com.dSystems.demo.Config.StorageProperties;
import com.dSystems.demo.Model.ChunkPlacement;
import com.dSystems.demo.Model.FileIndex;
import com.dSystems.demo.Repository.ChunkPlacementRepository;
import com.dSystems.demo.Repository.FileIndexRepository;
import com.dSystems.demo.Repository.StorageNodeRepository;
import com.dSystems.demo.Service.NodeRegistryService;
import com.dSystems.demo.Scheduler.ReReplicationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THE SYSTEM INTEGRATION TEST (THE END-TO-END SYSTEM INSPECTOR).
 * 
 * Think of this class as the ultimate test before ship-out. It doesn't mock or fake components;
 * it spins up the entire coordinator website, configures two mock storage node targets,
 * registers a real user, uploads a real file split into chunks, downloads it to verify it is correct,
 * simulates a storage server crashing, and then downloads it again to prove that the system is
 * fault-tolerant and survives server crashes without losing any data!
 * 
 * Annotations:
 * - @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT): Boots up the entire
 *   system on a random network port.
 * - @ActiveProfiles("test"): Uses testing configurations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DistributedStorageIntegrationTest {

    @LocalServerPort
    private int port;

    // Automatically inject real services and database helper desks:
    @Autowired
    private NodeRegistryService nodeRegistryService;

    @Autowired
    private ChunkPlacementRepository chunkPlacementRepository;

    @Autowired
    private FileIndexRepository fileIndexRepository;

    @Autowired
    private StorageNodeRepository storageNodeRepository;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private ReReplicationScheduler reReplicationScheduler;

    @BeforeEach
    void setUp() {
        chunkPlacementRepository.deleteAll();
        fileIndexRepository.deleteAll();
        storageNodeRepository.deleteAll();
    }

    /**
     * Test Case: Uploads a file, verifies replication, kills a server, and verifies download still works.
     */
    @Test
    void testEndToEndReplicationAndFaultTolerance() throws Exception {
        // Step 1: Set our system backup safety requirement to 2 copies per chunk
        storageProperties.setReplicationFactor(2);

        // Step 2: Register 2 storage servers in the coordinator's registry.
        // In our testing environment, both servers point to our local test port.
        nodeRegistryService.registerNode("node-1", "localhost", port);
        nodeRegistryService.registerNode("node-2", "localhost", port);

        // Step 3: Create a test user and log in to get a security admission ticket
        String username = "testuser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String registerPayload = """
                {
                  "username": "%s",
                  "email": "%s@example.com",
                  "password": "secret123"
                }
                """.formatted(username, username);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest registerReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(registerPayload))
                .build();
        client.send(registerReq, HttpResponse.BodyHandlers.ofString());

        String loginPayload = """
                {
                  "username": "%s",
                  "password": "secret123"
                }
                """.formatted(username);
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();
        HttpResponse<String> loginResponse = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        String token = loginResponse.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        // Step 4: Split the message "Hello World!" into 2 parts:
        // Part 0 = "Hello " (6 bytes)
        // Part 1 = "World!" (6 bytes)
        String fileId = "testfile-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] chunk1Bytes = "Hello ".getBytes();
        byte[] chunk2Bytes = "World!".getBytes();

        // Upload both chunks directly through the web API endpoints
        uploadChunkDirect(fileId, 0, 2, "test.txt", chunk1Bytes, token);
        uploadChunkDirect(fileId, 1, 2, "test.txt", chunk2Bytes, token);

        // Step 5: Verify the coordinator has correctly cataloged the backup placements.
        // Since replication factor = 2, and we have 2 healthy servers (node-1, node-2),
        // each chunk must have been placed on BOTH servers.
        List<ChunkPlacement> chunk1Placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 0);
        List<ChunkPlacement> chunk2Placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 1);

        assertEquals(2, chunk1Placements.size());
        assertEquals(2, chunk2Placements.size());

        // Verify the file status is marked COMPLETE and the size is correct (12 bytes)
        FileIndex index = fileIndexRepository.findByFileId(fileId).orElseThrow();
        assertEquals("COMPLETE", index.getStatus());
        assertEquals(12, index.getFileSize());

        // Step 6: Test downloading the file when both servers are alive.
        // It should fetch the pieces, stitch them back, and return "Hello World!".
        byte[] downloadedBytes = downloadFileDirect(fileId, token);
        assertEquals("Hello World!", new String(downloadedBytes));

        // Step 7: SIMULATE FAILURE.
        // Mark "node-1" as DEAD. This simulates node-1 crashing or going offline.
        nodeRegistryService.markNodeDead("node-1");

        // Step 7.5: Register node-3 to act as target candidate for healing
        nodeRegistryService.registerNode("node-3", "localhost", port);

        // Step 7.6: Run the healer to re-replicate chunk copies to node-3
        reReplicationScheduler.healReplicas();

        // Verify placements for chunks. They should now be on node-2 and node-3.
        List<ChunkPlacement> chunk1PlacementsAfterHeal = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 0);
        List<ChunkPlacement> chunk2PlacementsAfterHeal = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 1);

        assertEquals(2, chunk1PlacementsAfterHeal.size());
        assertEquals(2, chunk2PlacementsAfterHeal.size());

        assertTrue(chunk1PlacementsAfterHeal.stream().anyMatch(cp -> "node-3".equals(cp.getNodeId())));
        assertTrue(chunk2PlacementsAfterHeal.stream().anyMatch(cp -> "node-3".equals(cp.getNodeId())));

        // Step 8: Test downloading the file AGAIN.
        // It should download correctly from node-2 and/or node-3.
        byte[] downloadedBytesAfterFailure = downloadFileDirect(fileId, token);
        assertEquals("Hello World!", new String(downloadedBytesAfterFailure));
    }

    @Test
    void testWholeFileUploadAndFaultTolerance() throws Exception {
        // Step 1: Set our system backup safety requirement to 2 copies per chunk
        storageProperties.setReplicationFactor(2);

        // Step 2: Register 2 storage servers in the coordinator's registry.
        nodeRegistryService.registerNode("node-4", "localhost", port);
        nodeRegistryService.registerNode("node-5", "localhost", port);

        // Step 3: Create a test user and log in
        String username = "wholefileuser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String registerPayload = """
                {
                  "username": "%s",
                  "email": "%s@example.com",
                  "password": "secret123"
                }
                """.formatted(username, username);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest registerReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(registerPayload))
                .build();
        client.send(registerReq, HttpResponse.BodyHandlers.ofString());

        String loginPayload = """
                {
                  "username": "%s",
                  "password": "secret123"
                }
                """.formatted(username);
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();
        HttpResponse<String> loginResponse = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        String token = loginResponse.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        // Step 4: Perform whole file upload
        String testContentString = "A".repeat(120 * 1024);
        byte[] contentBytes = testContentString.getBytes();

        Path tempFile = Files.createTempFile("whole-file-test-", ".txt");
        Files.write(tempFile, contentBytes);

        RestTemplate restTemplate = new RestTemplate();
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.FileSystemResource(tempFile.toFile()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + token);

        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/files/upload",
                requestEntity,
                String.class
        );

        Files.deleteIfExists(tempFile);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());

        String responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.contains("with ID file-"));
        String fileId = responseBody.replaceAll(".*with ID (file-\\w+).*", "$1").trim();

        // Step 5: Verify the coordinator has correctly cataloged the backup placements.
        FileIndex index = fileIndexRepository.findByFileId(fileId).orElseThrow();
        assertEquals("COMPLETE", index.getStatus());
        assertEquals(contentBytes.length, index.getFileSize());
        int totalChunks = index.getTotalChunks();
        assertTrue(totalChunks > 0);

        for (int i = 0; i < totalChunks; i++) {
            List<ChunkPlacement> placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, i);
            assertEquals(2, placements.size());
        }

        // Step 6: Test downloading the file.
        byte[] downloadedBytes = downloadFileDirect(fileId, token);
        assertEquals(testContentString, new String(downloadedBytes));
    }

    /**
     * Helper Method: Performs a multipart HTTP POST to upload a chunk file to the system.
     */
    private void uploadChunkDirect(String fileId, int chunkNumber, int totalChunks, String filename, byte[] content, String token) throws Exception {
        // Write the chunk bytes to a local temporary file first
        Path tempFile = Files.createTempFile("chunk-test-", ".part");
        Files.write(tempFile, content);

        RestTemplate restTemplate = new RestTemplate();
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("fileId", fileId);
        body.add("chunkNumber", chunkNumber);
        body.add("totalChunks", totalChunks);
        body.add("identifier", fileId);
        body.add("file", new org.springframework.core.io.FileSystemResource(tempFile.toFile()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + token); // Attach auth ticket

        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/files/chunks",
                requestEntity,
                String.class
        );

        // Delete the temp file to keep the disk clean
        Files.deleteIfExists(tempFile);
        
        // Assert: Verify upload was successful (HTTP 200 OK or 202 Accepted)
        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value() == 202 ? HttpStatus.OK.value() : response.getStatusCode().value());
    }

    /**
     * Helper Method: Performs an HTTP GET request to download a file from the system.
     */
    private byte[] downloadFileDirect(String fileId, String token) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/files/download?identifier=" + fileId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        return response.body();
    }
}
