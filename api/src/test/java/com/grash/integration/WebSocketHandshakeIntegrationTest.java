package com.grash.integration;

import com.grash.factory.MailServiceFactory;
import com.grash.model.Company;
import com.grash.model.CompanySettings;
import com.grash.model.GeneralPreferences;
import com.grash.model.Role;
import com.grash.model.Subscription;
import com.grash.model.SubscriptionPlan;
import com.grash.model.User;
import com.grash.model.enums.DateFormat;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.repository.CompanyRepository;
import com.grash.repository.CompanySettingsRepository;
import com.grash.repository.CurrencyRepository;
import com.grash.repository.GeneralPreferencesRepository;
import com.grash.repository.RoleRepository;
import com.grash.repository.SubscriptionPlanRepository;
import com.grash.repository.SubscriptionRepository;
import com.grash.repository.UserRepository;
import com.grash.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real SockJS/STOMP path that the browser uses on Railway: the info
 * request, the CORS preflight, the WebSocket handshake and an authenticated STOMP
 * CONNECT. The test owns its database so it cannot disturb the shared integration
 * fixtures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResourceLock("cmms-test-db")
class WebSocketHandshakeIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("cmms_ws_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @MockitoBean
    private MailServiceFactory mailServiceFactory;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private SimpUserRegistry simpUserRegistry;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanySettingsRepository companySettingsRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired
    private CurrencyRepository currencyRepository;
    @Autowired
    private GeneralPreferencesRepository generalPreferencesRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userEmail;

    @BeforeEach
    void seedUser() {
        userEmail = "ws.probe-" + UUID.randomUUID() + "@test.local";

        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name("WS Test Plan " + UUID.randomUUID())
                .monthlyCostPerUser(10.0)
                .yearlyCostPerUser(100.0)
                .features(new HashSet<>())
                .build());

        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .usersCount(10)
                .subscriptionPlan(plan)
                .build());

        Company company = companyRepository.save(new Company("WS Test Company", 10, subscription));

        CompanySettings companySettings = company.getCompanySettings();
        companySettings.setCompany(company);
        companySettings = companySettingsRepository.save(companySettings);

        GeneralPreferences generalPreferences = companySettings.getGeneralPreferences();
        generalPreferences.setCurrency(currencyRepository.findFirstBy().orElseThrow());
        generalPreferences.setDateFormat(DateFormat.MMDDYY);
        generalPreferences.setTimeZone("UTC");
        generalPreferencesRepository.save(generalPreferences);

        Role role = roleRepository.save(Role.builder()
                .name("WS Test Admin")
                .roleType(RoleType.ROLE_CLIENT)
                .code(RoleCode.ADMIN)
                .companySettings(companySettings)
                .paid(true)
                .build());

        User user = new User();
        user.setFirstName("WS");
        user.setLastName("Probe");
        user.setEmail(userEmail);
        user.setUsername("wsprobe-" + UUID.randomUUID());
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(role);
        user.setCompany(company);
        user.setEnabled(true);
        user.setEnabledInSubscription(true);
        userRepository.save(user);
    }

    @Test
    void sockJsInfo_allowsConfiguredFrontendOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ALLOWED_ORIGIN);

        ResponseEntity<String> response = restTemplate.exchange(
                "/ws/info?t=123", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ALLOWED_ORIGIN, response.getHeaders().getAccessControlAllowOrigin());
        assertTrue(response.getBody() != null && response.getBody().contains("\"websocket\":true"),
                "Expected SockJS info payload, got: " + response.getBody());
    }

    @Test
    void sockJsInfo_rejectsUnknownOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://evil.example.com");

        ResponseEntity<String> response = restTemplate.exchange(
                "/ws/info?t=123", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNull(response.getHeaders().getAccessControlAllowOrigin());
    }

    @Test
    void sockJsInfo_preflightAllowsConfiguredFrontendOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ALLOWED_ORIGIN);
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        headers.setAccessControlRequestHeaders(List.of("Content-Type"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/ws/info?t=123", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "Preflight failed with " + response.getStatusCode());
        assertEquals(ALLOWED_ORIGIN, response.getHeaders().getAccessControlAllowOrigin());
        assertTrue(response.getHeaders().getAccessControlAllowMethods().contains(HttpMethod.GET));
    }

    @Test
    void handshakeAndAuthenticatedStompDeliversUserMessage() throws Exception {
        StompSession session = null;
        WebSocketStompClient stompClient = stompClient();
        try {
            String token = jwtTokenProvider.createToken(userEmail, List.of(RoleType.ROLE_CLIENT));
            session = connect(stompClient, token);
            assertTrue(session.isConnected());

            CompletableFuture<Map<String, Object>> received = new CompletableFuture<>();
            session.subscribe("/user/" + userEmail + "/notifications", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    received.complete((Map<String, Object>) payload);
                }
            });

            await().atMost(Duration.ofSeconds(10)).until(() -> hasUserSubscription());

            messagingTemplate.convertAndSendToUser(userEmail, "/notifications",
                    Map.of("title", "ws-probe"));

            Map<String, Object> payload = received.get(10, TimeUnit.SECONDS);
            assertEquals("ws-probe", payload.get("title"));
        } finally {
            if (session != null) {
                session.disconnect();
            }
            stompClient.stop();
        }
    }

    @Test
    void handshake_rejectsUnknownOrigin() {
        WebSocketStompClient stompClient = stompClient();
        try {
            String token = jwtTokenProvider.createToken(userEmail, List.of(RoleType.ROLE_CLIENT));
            assertThrows(ExecutionException.class, () ->
                    connectAsync(stompClient, token, "https://evil.example.com")
                            .get(15, TimeUnit.SECONDS));
        } finally {
            stompClient.stop();
        }
    }

    @Test
    void stompConnect_withInvalidToken_isRejected() {
        WebSocketStompClient stompClient = stompClient();
        try {
            assertThrows(ExecutionException.class, () ->
                    connectAsync(stompClient, "not-a-valid-jwt", ALLOWED_ORIGIN)
                            .get(15, TimeUnit.SECONDS));
        } finally {
            stompClient.stop();
        }
    }

    private boolean hasUserSubscription() {
        var simpUser = simpUserRegistry.getUser(userEmail);
        return simpUser != null && simpUser.getSessions().stream()
                .anyMatch(simpSession -> simpSession.getSubscriptions().stream()
                        .anyMatch(subscription -> ("/user/" + userEmail + "/notifications")
                                .equals(subscription.getDestination())));
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    private StompSession connect(WebSocketStompClient stompClient, String token) throws Exception {
        return connectAsync(stompClient, token, ALLOWED_ORIGIN).get(15, TimeUnit.SECONDS);
    }

    private CompletableFuture<StompSession> connectAsync(WebSocketStompClient stompClient,
                                                         String token,
                                                         String origin) {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin(origin);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("token", token);
        return stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/websocket",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                });
    }
}
