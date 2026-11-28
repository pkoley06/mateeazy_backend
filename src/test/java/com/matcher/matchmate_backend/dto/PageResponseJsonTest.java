package com.matcher.matchmate_backend.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Clients read "isLast" to know whether to fetch the next page. */
class PageResponseJsonTest {

    @Test
    void sendsIsLast() {
        PageResponse<String> firstOfTwo = PageResponse.of(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 4), s -> s);

        JsonNode json = JsonMapper.builder().build().valueToTree(firstOfTwo);

        assertThat(json.has("isLast")).isTrue();
        assertThat(json.get("isLast").asBoolean()).isFalse();
        assertThat(json.get("totalPages").asInt()).isEqualTo(2);
    }
}
