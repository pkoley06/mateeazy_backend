package com.matcher.matchmate_backend;

import com.matcher.matchmate_backend.model.entity.AdminUser;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.PrimaryAuthProvider;
import com.matcher.matchmate_backend.repository.AdminUserRepository;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminUserRepository adminUserRepository;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private AppUser sampleUser;

    @BeforeEach
    void setUp() {
        listingRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();

        AdminUser admin = adminUserRepository.save(AdminUser.builder()
                .username("superadmin")
                .passwordHash(passwordEncoder.encode("SecretPass123!"))
                .displayName("Super Admin")
                .build());

        adminToken = jwtTokenProvider.generateAdminToken(admin.getId(), admin.getUsername());

        sampleUser = userRepository.save(AppUser.builder()
                .firebaseUid("user-uid-1")
                .primaryAuthProvider(PrimaryAuthProvider.GOOGLE)
                .fullName("Alex Doe")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1996, 4, 15))
                .lastKnownLocality("Salt Lake")
                .onboardingCompleted(true)
                .build());
    }

    @Test
    void adminLoginSuccessAndFailure() throws Exception {
        Map<String, Object> validBody = Map.of(
                "username", "superadmin",
                "password", "SecretPass123!"
        );

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("superadmin"))
                .andExpect(jsonPath("$.displayName").value("Super Admin"));

        Map<String, Object> badPass = Map.of(
                "username", "superadmin",
                "password", "WrongPassword"
        );

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPass)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUserCrudFlow() throws Exception {
        // List users
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Get user by ID
        mockMvc.perform(get("/api/v1/admin/users/" + sampleUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alex Doe"));

        // Create new user via admin
        Map<String, Object> createReq = Map.of(
                "fullName", "Admin Created User",
                "gender", "FEMALE",
                "dateOfBirth", "2000-01-01",
                "lastKnownLocality", "New Town"
        );

        String createRes = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Admin Created User"))
                .andExpect(jsonPath("$.primaryAuthProvider").value("ADMIN_CREATED"))
                .andReturn().getResponse().getContentAsString();

        String newUserId = com.jayway.jsonpath.JsonPath.read(createRes, "$.id");

        // Toggle active
        mockMvc.perform(patch("/api/v1/admin/users/" + newUserId + "/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void adminListingCrudFlow() throws Exception {
        Map<String, Object> listingReq = new LinkedHashMap<>();
        listingReq.put("hostUserId", sampleUser.getId());
        listingReq.put("listingType", "HAVE_ROOM");
        listingReq.put("title", "Modern 2BHK Master Bedroom");
        listingReq.put("rentAmount", 12000);
        listingReq.put("depositAmount", 24000);
        listingReq.put("roomType", "PRIVATE");
        listingReq.put("furnishingStatus", "FURNISHED");
        listingReq.put("latitude", 22.5726);
        listingReq.put("longitude", 88.3639);
        listingReq.put("localityName", "Salt Lake Sector V");
        listingReq.put("city", "Kolkata");
        listingReq.put("description", "Spacious room with attached washroom");
        listingReq.put("photoUrls", List.of("https://example.com/p1.jpg"));
        listingReq.put("amenities", List.of("WIFI", "AC"));

        String createdJson = mockMvc.perform(post("/api/v1/admin/listings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(listingReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Modern 2BHK Master Bedroom"))
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andReturn().getResponse().getContentAsString();

        String listingId = com.jayway.jsonpath.JsonPath.read(createdJson, "$.id");

        // List listings
        mockMvc.perform(get("/api/v1/admin/listings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Toggle availability
        mockMvc.perform(patch("/api/v1/admin/listings/" + listingId + "/available")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));

        // Delete listing
        mockMvc.perform(delete("/api/v1/admin/listings/" + listingId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminBrandingAndSettingsFlow() throws Exception {
        // Public get branding without token
        mockMvc.perform(get("/api/v1/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("MatchMate"));

        // Admin update branding
        Map<String, Object> update = Map.of(
                "appName", "MatchMate Pro",
                "tagline", "Next-Gen Roommate Matching",
                "primaryColor", "#1E88E5",
                "supportEmail", "help@matchmate.io"
        );

        mockMvc.perform(put("/api/v1/admin/branding")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("MatchMate Pro"))
                .andExpect(jsonPath("$.primaryColor").value("#1E88E5"));
    }

    @Test
    void adminQuestionnaireAndWeightsFlow() throws Exception {
        mockMvc.perform(get("/api/v1/admin/questionnaire")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.currentWeights.cleanlinessWeight").value(2.0));

        Map<String, Object> weightUpdate = Map.of(
                "cleanlinessWeight", 2.5,
                "sleepWeight", 1.8,
                "guestWeight", 1.0,
                "dietWeight", 0.8,
                "smokingDealbreaker", true
        );

        mockMvc.perform(put("/api/v1/admin/questionnaire/weights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weightUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleanlinessWeight").value(2.5));
    }

    @Test
    void adminDashboardStatsFlow() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.activeUsers").value(1))
                .andExpect(jsonPath("$.recentUsers").isArray());
    }

    @Test
    void unauthorizedAccessWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/listings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }
}
