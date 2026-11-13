package com.matcher.matchmate_backend;

import tools.jackson.databind.ObjectMapper;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.PrimaryAuthProvider;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommunityIntegrationTest {

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

    private String userToken;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String firebaseUid = "comm-user-" + System.nanoTime();
        AppUser user = userRepository.save(AppUser.builder()
                .firebaseUid(firebaseUid)
                .primaryAuthProvider(PrimaryAuthProvider.PHONE)
                .fullName("Pritam Koley")
                .phoneNumber("+919999988888")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1998, 1, 1))
                .isActive(true)
                .isSuspended(false)
                .build());
        userId = user.getId();
        userToken = jwtTokenProvider.generateToken(userId, firebaseUid);
    }

    @Test
    void testCommunityPostLifecycle() throws Exception {
        // 1. Create a post
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("category", CommunityPostCategory.MARKETPLACE.name());
        createReq.put("title", "Selling Wooden Study Table");
        createReq.put("content", "Used for 6 months, excellent condition. Pick up from Koramangala 4th Block.");
        createReq.put("priceAmount", 2500);
        createReq.put("latitude", 12.9352);
        createReq.put("longitude", 77.6245);
        createReq.put("localityName", "Koramangala 4th Block");
        createReq.put("city", "Bengaluru");
        createReq.put("imageUrls", List.of("https://example.com/table.jpg"));

        String createJson = objectMapper.writeValueAsString(createReq);

        String postResponseStr = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Selling Wooden Study Table"))
                .andExpect(jsonPath("$.priceAmount").value(2500))
                .andExpect(jsonPath("$.likesCount").value(0))
                .andExpect(jsonPath("$.commentsCount").value(0))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> postResp = objectMapper.readValue(postResponseStr, Map.class);
        String postId = (String) postResp.get("id");

        // 2. Fetch feed within 10 km
        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + userToken)
                        .param("lat", "12.9350")
                        .param("lng", "77.6240")
                        .param("radiusKm", "10")
                        .param("category", "MARKETPLACE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(postId))
                .andExpect(jsonPath("$.content[0].authorName").value("Pritam Koley"))
                .andExpect(jsonPath("$.content[0].isLikedByMe").value(false));

        // 3. Like the post
        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likesCount").value(1));

        // 4. Verify post detail shows liked
        mockMvc.perform(get("/api/v1/community/posts/" + postId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLikedByMe").value(true))
                .andExpect(jsonPath("$.likesCount").value(1));

        // 5. Add a comment
        Map<String, Object> commentReq = Map.of("content", "Is this still available? Can pick up tomorrow.");
        String commentResponseStr = mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.content").value("Is this still available? Can pick up tomorrow."))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> commentResp = objectMapper.readValue(commentResponseStr, Map.class);
        String commentId = (String) commentResp.get("id");

        // 6. Fetch comments
        mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(commentId));

        // 7. Delete comment
        mockMvc.perform(delete("/api/v1/community/comments/" + commentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // 8. Delete post
        mockMvc.perform(delete("/api/v1/community/posts/" + postId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());
    }
}
