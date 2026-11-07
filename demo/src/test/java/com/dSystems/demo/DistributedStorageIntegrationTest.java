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
 * End-to-end tests of the upload and download paths against a running server, including download
 * after a node failure and re-replication. All registered nodes resolve to the test server's own
 * port, so a single instance serves both the coordinator and storage node APIs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DistributedStorageIntegrationTest {

    @LocalServerPort
    private int port;

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
     * Uploads a file in chunks, fails a node, re-replicates, and verifies the file is still intact.
     */
    @Test
    void testEndToEndReplicationAndFaultTolerance() throws Exception {
        // Step 1: Set the replication factor to 2.
        storageProperties.setReplicationFactor(2);

        // Step 2: Register two storage nodes, both pointing at the test server.
        nodeRegistryService.registerNode("node-1", "localhost", port);
        nodeRegistryService.registerNode("node-2", "localhost", port);

        // Step 3: Create a test user and log in.
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

        // Step 4: Upload "Hello World!" as two 6-byte chunks.
        String fileId = "testfile-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] chunk1Bytes = "Hello ".getBytes();
        byte[] chunk2Bytes = "World!".getBytes();

        uploadChunkDirect(fileId, 0, 2, "test.txt", chunk1Bytes, token);
        uploadChunkDirect(fileId, 1, 2, "test.txt", chunk2Bytes, token);

        // Step 5: With a replication factor of 2 and two nodes, each chunk must be on both nodes.
        List<ChunkPlacement> chunk1Placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 0);
        List<ChunkPlacement> chunk2Placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 1);

        assertEquals(2, chunk1Placements.size());
        assertEquals(2, chunk2Placements.size());

        // The file must be COMPLETE with a size of 12 bytes.
        FileIndex index = fileIndexRepository.findByFileId(fileId).orElseThrow();
        assertEquals("COMPLETE", index.getStatus());
        assertEquals(12, index.getFileSize());

        // Step 6: Download the file with both nodes alive.
        byte[] downloadedBytes = downloadFileDirect(fileId, token);
        assertEquals("Hello World!", new String(downloadedBytes));

        // Step 7: Simulate the failure of node-1.
        nodeRegistryService.markNodeDead("node-1");

        // Step 7.5: Register node-3 as a re-replication target.
        nodeRegistryService.registerNode("node-3", "localhost", port);

        // Step 7.6: Run re-replication.
        reReplicationScheduler.healReplicas();

        // Each chunk must now be on node-2 and node-3.
        List<ChunkPlacement> chunk1PlacementsAfterHeal = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 0);
        List<ChunkPlacement> chunk2PlacementsAfterHeal = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 1);

        assertEquals(2, chunk1PlacementsAfterHeal.size());
        assertEquals(2, chunk2PlacementsAfterHeal.size());

        assertTrue(chunk1PlacementsAfterHeal.stream().anyMatch(cp -> "node-3".equals(cp.getNodeId())));
        assertTrue(chunk2PlacementsAfterHeal.stream().anyMatch(cp -> "node-3".equals(cp.getNodeId())));

        // Step 8: Download the file again from the surviving replicas.
        byte[] downloadedBytesAfterFailure = downloadFileDirect(fileId, token);
        assertEquals("Hello World!", new String(downloadedBytesAfterFailure));
    }

    @Test
    void testWholeFileUploadAndFaultTolerance() throws Exception {
        // Step 1: Set the replication factor to 2.
        storageProperties.setReplicationFactor(2);

        // Step 2: Register two storage nodes.
        nodeRegistryService.registerNode("node-4", "localhost", port);
        nodeRegistryService.registerNode("node-5", "localhost", port);

        // Step 3: Create a test user and log in.
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

        // Step 4: Upload the whole file.
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

        // Step 5: Verify the recorded chunk placements.
        FileIndex index = fileIndexRepository.findByFileId(fileId).orElseThrow();
        assertEquals("COMPLETE", index.getStatus());
        assertEquals(contentBytes.length, index.getFileSize());
        int totalChunks = index.getTotalChunks();
        assertTrue(totalChunks > 0);

        for (int i = 0; i < totalChunks; i++) {
            List<ChunkPlacement> placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, i);
            assertEquals(2, placements.size());
        }

        // Step 6: Download the file.
        byte[] downloadedBytes = downloadFileDirect(fileId, token);
        assertEquals(testContentString, new String(downloadedBytes));
    }

    /**
     * Uploads one chunk through the API and asserts that the request succeeds.
     */
    private void uploadChunkDirect(String fileId, int chunkNumber, int totalChunks, String filename, byte[] content, String token) throws Exception {
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
        headers.set("Authorization", "Bearer " + token);

        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/files/chunks",
                requestEntity,
                String.class
        );

        Files.deleteIfExists(tempFile);
        
        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value() == 202 ? HttpStatus.OK.value() : response.getStatusCode().value());
    }

    /**
     * Downloads a file through the API.
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
