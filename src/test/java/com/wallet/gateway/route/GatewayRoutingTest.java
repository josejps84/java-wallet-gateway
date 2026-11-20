package com.wallet.gateway.route;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the gateway on a random port in front of a stub HTTP server that plays both downstream services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=" + GatewayRoutingTest.SECRET)
class GatewayRoutingTest {

    static final String SECRET = "test-secret-test-secret-test-secret-1234";

    /** What the stub received: "METHOD path?query | Authorization header". */
    static final List<String> DOWNSTREAM_REQUESTS = new CopyOnWriteArrayList<>();
    static final HttpServer DOWNSTREAM = startDownstream();

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
        registry.add("auth.url", () -> url);
        registry.add("account.url", () -> url);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void reset() {
        DOWNSTREAM_REQUESTS.clear();
    }

    @Test
    void loginAndRegisterArePublicAndProxied() throws Exception {
        assertThat(send(post("/api/users/login")).statusCode()).isEqualTo(200);
        assertThat(send(post("/api/users/register")).statusCode()).isEqualTo(200);

        assertThat(DOWNSTREAM_REQUESTS).containsExactly("POST /api/users/login | null",
                "POST /api/users/register | null");
    }

    @Test
    void otherUserEndpointsAreNotRouted() throws Exception {
        assertThat(send(get("/api/users/1").build()).statusCode()).isEqualTo(404);
        assertThat(send(get("/api/users/login").build()).statusCode()).isEqualTo(404);
        assertThat(DOWNSTREAM_REQUESTS).isEmpty();
    }

    @Test
    void accountRoutesRejectMissingOrInvalidTokens() throws Exception {
        HttpResponse<String> noToken = send(get("/api/accounts").build());
        assertThat(noToken.statusCode()).isEqualTo(401);
        assertThat(noToken.body()).contains("\"detail\"");

        assertThat(send(get("/api/accounts/new").header("Authorization", "Bearer forged").build()).statusCode())
                .isEqualTo(401);
        assertThat(send(get("/api/accounts").header("Authorization", "Basic abc").build()).statusCode())
                .isEqualTo(401);
        assertThat(DOWNSTREAM_REQUESTS).isEmpty();
    }

    @Test
    void validTokenIsForwardedWithPathAndQuery() throws Exception {
        String bearer = "Bearer " + token();

        HttpResponse<String> response = send(get("/api/accounts?page=1&pageSize=5").header("Authorization", bearer)
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"ok\":true}");
        assertThat(DOWNSTREAM_REQUESTS).containsExactly("GET /api/accounts?page=1&pageSize=5 | " + bearer);
    }

    @Test
    void downstreamErrorsArePassedThrough() throws Exception {
        HttpResponse<String> response = send(get("/api/accounts/forbidden").header("Authorization", "Bearer " + token())
                .build());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("denied");
    }

    @Test
    void corsPreflightOnlyForAllowedOrigins() throws Exception {
        HttpResponse<String> allowed = send(preflight("http://localhost:5173"));
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");

        HttpResponse<String> rejected = send(preflight("https://evil.example.com"));
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(DOWNSTREAM_REQUESTS).isEmpty();
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private HttpRequest post(String path) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
    }

    private HttpRequest preflight(String origin) {
        return HttpRequest.newBuilder(uri("/api/accounts"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String token() {
        return Jwts.builder()
                .subject("user@example.com")
                .claim("userId", 1L)
                .claim("userType", "CUSTOMER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                URI uri = exchange.getRequestURI();
                DOWNSTREAM_REQUESTS.add(exchange.getRequestMethod() + " " + uri.getPath()
                        + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "")
                        + " | " + exchange.getRequestHeaders().getFirst("Authorization"));
                boolean forbidden = uri.getPath().endsWith("/forbidden");
                byte[] body = (forbidden ? "{\"detail\":\"denied\"}" : "{\"ok\":true}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(forbidden ? 403 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
