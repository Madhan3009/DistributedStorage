package com.dSystems.demo;

import com.dSystems.demo.Repository.StorageNodeRepository;
import com.dSystems.demo.Security.ClusterAuthenticationFilter;
import com.dSystems.demo.Service.NodeRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies access control on cluster and node management endpoints, and that bearer tokens are not
 * written to the logs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ClusterSecurityTest {

    private static final String CLUSTER_HEADER = ClusterAuthenticationFilter.HEADER_NAME;

    @LocalServerPort
    private int port;

    @Value("${app.cluster.secret}")
    private String clusterSecret;

    @Autowired
    private NodeRegistryService nodeRegistryService;

    @Autowired
    private StorageNodeRepository storageNodeRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void internalEndpoints_requireClusterToken() throws Exception {
        assertEquals(401, send("GET", "/internal/health").statusCode());
        assertEquals(401, send("GET", "/internal/health", CLUSTER_HEADER, "wrong-secret").statusCode());
        assertEquals(200, send("GET", "/internal/health", CLUSTER_HEADER, clusterSecret).statusCode());
    }

    @Test
    void internalEndpoints_rejectUserTokens() throws Exception {
        assertEquals(403, send("GET", "/internal/health", "Authorization", "Bearer " + loginNewUser()).statusCode());
    }

    @Test
    void nodeRegistrationAndHeartbeat_requireClusterToken() throws Exception {
        String nodeId = "node-" + UUID.randomUUID();
        String registerPath = "/nodes/register?nodeId=" + nodeId + "&host=localhost&port=1";
        String heartbeatPath = "/nodes/heartbeat?nodeId=" + nodeId + "&diskUsed=1&diskFree=1";

        assertEquals(401, send("POST", registerPath).statusCode());
        assertFalse(storageNodeRepository.findByNodeId(nodeId).isPresent());
        assertEquals(401, send("POST", heartbeatPath).statusCode());

        assertEquals(200, send("POST", registerPath, CLUSTER_HEADER, clusterSecret).statusCode());
        assertEquals(200, send("POST", heartbeatPath, CLUSTER_HEADER, clusterSecret).statusCode());
    }

    @Test
    void nodeRegistrationAndHeartbeat_rejectUserTokens() throws Exception {
        String path = "/nodes/register?nodeId=node-" + UUID.randomUUID() + "&host=localhost&port=1";
        assertEquals(403, send("POST", path, "Authorization", "Bearer " + loginNewUser()).statusCode());
    }

    @Test
    void nodeListingAndFailure_requireUserLogin() throws Exception {
        String nodeId = "node-" + UUID.randomUUID();
        nodeRegistryService.registerNode(nodeId, "localhost", 1);

        assertEquals(401, send("GET", "/nodes").statusCode());
        assertEquals(401, send("POST", "/nodes/" + nodeId + "/fail").statusCode());
        assertEquals("ALIVE", storageNodeRepository.findByNodeId(nodeId).orElseThrow().getStatus());

        String bearer = "Bearer " + loginNewUser();
        assertEquals(200, send("GET", "/nodes", "Authorization", bearer).statusCode());
        assertEquals(200, send("POST", "/nodes/" + nodeId + "/fail", "Authorization", bearer).statusCode());
        assertEquals("DEAD", storageNodeRepository.findByNodeId(nodeId).orElseThrow().getStatus());
    }

    @Test
    void clusterToken_doesNotGrantAccessToUserApis() throws Exception {
        assertEquals(401, send("GET", "/files", CLUSTER_HEADER, clusterSecret).statusCode());
        assertEquals(401, send("GET", "/nodes", CLUSTER_HEADER, clusterSecret).statusCode());
    }

    @Test
    void bearerTokens_areNotLogged(CapturedOutput output) throws Exception {
        String token = loginNewUser();
        assertEquals(200, send("GET", "/api/me", "Authorization", "Bearer " + token).statusCode());
        assertFalse(output.getAll().contains(token));
    }

    @Test
    void shortClusterSecret_isRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new ClusterAuthenticationFilter("too-short"));
    }

    private HttpResponse<String> send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String loginNewUser() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        post("/auth/register", """
                {"username": "%s", "email": "%s@example.com", "password": "secret123"}
                """.formatted(username, username));
        String body = post("/auth/login", """
                {"username": "%s", "password": "secret123"}
                """.formatted(username)).body();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
