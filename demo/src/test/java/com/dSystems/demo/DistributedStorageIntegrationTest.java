package com.dSystems.demo;

import com.dSystems.demo.Config.StorageProperties;
import com.dSystems.demo.Model.ChunkPlacement;
import com.dSystems.demo.Model.FileIndex;
import com.dSystems.demo.Repository.ChunkPlacementRepository;
import com.dSystems.demo.Repository.FileIndexRepository;
import com.dSystems.demo.Service.NodeRegistryService;
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
    private StorageProperties storageProperties;

    @Test
    void testEndToEndReplicationAndFaultTolerance() throws Exception {
        // Set replication factor to 2
        storageProperties.setReplicationFactor(2);

        // 1. Register 2 storage nodes pointing back to this running instance
        nodeRegistryService.registerNode("node-1", "localhost", port);
        nodeRegistryService.registerNode("node-2", "localhost", port);

        // 2. Register/Login user to get JWT token
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

        // 3. Upload a file divided into 2 chunks
        String fileId = "testfile-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] chunk1Bytes = "Hello ".getBytes();
        byte[] chunk2Bytes = "World!".getBytes();

        uploadChunkDirect(fileId, 0, 2, "test.txt", chunk1Bytes, token);
        uploadChunkDirect(fileId, 1, 2, "test.txt", chunk2Bytes, token);

        // 4. Verify placements in database
        List<ChunkPlacement> chunk1Placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 0);
        List<ChunkPlacement> chunk2Placements = chunkPlacementRepository.findByFileIdAndChunkNumber(fileId, 1);

        // Since replicationFactor = 2 and we have 2 alive nodes, both chunks must be replicated on both node-1 and node-2
        assertEquals(2, chunk1Placements.size());
        assertEquals(2, chunk2Placements.size());

        // Verify file index status is COMPLETE
        FileIndex index = fileIndexRepository.findByFileId(fileId).orElseThrow();
        assertEquals("COMPLETE", index.getStatus());
        assertEquals(12, index.getFileSize());

        // 5. Test download when both nodes are alive
        byte[] downloadedBytes = downloadFileDirect(fileId, token);
        assertEquals("Hello World!", new String(downloadedBytes));

        // 6. Simulate node-1 failing
        nodeRegistryService.markNodeDead("node-1");

        // 7. Verify download still succeeds (it will fallback to node-2 for any chunk that was placed on node-1)
        byte[] downloadedBytesAfterFailure = downloadFileDirect(fileId, token);
        assertEquals("Hello World!", new String(downloadedBytesAfterFailure));
    }

    private void uploadChunkDirect(String fileId, int chunkNumber, int totalChunks, String filename, byte[] content, String token) throws Exception {
        // Write content to a temp file to send as multipart
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
