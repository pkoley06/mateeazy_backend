package com.matcher.matchmate_backend;

import tools.jackson.databind.ObjectMapper;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.PrimaryAuthProvider;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Milestone 5 (minimal): POST/GET /api/v1/listings end-to-end against real PostGIS. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ListingControllerIntegrationTest {

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
    private AppUserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ObjectMapper objectMapper;

    private String hostToken;

    @BeforeEach
    void seedHost() {
        userRepository.deleteAll();
        AppUser host = userRepository.save(AppUser.builder()
                .firebaseUid("host-uid")
                .primaryAuthProvider(PrimaryAuthProvider.GOOGLE)
                .fullName("Host User")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1995, 5, 20))
                .onboardingCompleted(true)
                .build());
        hostToken = jwtTokenProvider.generateToken(host.getId(), host.getFirebaseUid());
    }

    @Test
    void createThenFetchListingRoundTrips() throws Exception {
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("listingType", "HAVE_ROOM");
        createRequest.put("title", "Sunny room, quiet street");
        createRequest.put("rentAmount", 9000);
        createRequest.put("depositAmount", 18000);
        createRequest.put("roomType", "PRIVATE");
        createRequest.put("furnishingStatus", "SEMI_FURNISHED");
        createRequest.put("latitude", 22.5697);
        createRequest.put("longitude", 88.4325);
        createRequest.put("localityName", "Salt Lake, Sector V");
        createRequest.put("city", "Kolkata");
        createRequest.put("description", "Spacious single room.");
        createRequest.put("photoUrls", List.of("https://example.com/room1.jpg"));

        String createResponse = mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.hostUserId").exists())
                .andReturn().getResponse().getContentAsString();

        String listingId = (String) objectMapper.readValue(createResponse, Map.class).get("id");

        mockMvc.perform(get("/api/v1/listings/" + listingId).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localityName").value("Salt Lake, Sector V"))
                .andExpect(jsonPath("$.latitude").value(22.5697))
                .andExpect(jsonPath("$.longitude").value(88.4325))
                .andExpect(jsonPath("$.photoUrls[0]").value("https://example.com/room1.jpg"));

        mockMvc.perform(get("/api/v1/listings/my").header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(listingId));
    }

    @Test
    void invalidPayloadIsRejected() throws Exception {
        Map<String, Object> missingFields = Map.of("listingType", "HAVE_ROOM");

        mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingFields)))
                .andExpect(status().isBadRequest());
    }
}
