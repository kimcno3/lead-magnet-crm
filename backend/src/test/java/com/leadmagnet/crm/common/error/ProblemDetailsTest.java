package com.leadmagnet.crm.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ProblemDetailsTest {

    @Test
    void write는_MVC와_같은_모양으로_상태_형식_본문을_쓰고_instance에_요청_경로를_넣는다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetails.write(new MockHttpServletRequest("POST", "/api/public/forms/a/submissions"), response,
            ErrorCode.FORBIDDEN_ORIGIN);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        JsonNode body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertThat(body.get("type").asText()).isEqualTo("https://leadmagnet.example/problems/forbidden_origin");
        assertThat(body.get("title").asText()).isEqualTo("Forbidden");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("detail").asText()).isEqualTo(ErrorCode.FORBIDDEN_ORIGIN.defaultDetail());
        assertThat(body.get("instance").asText()).isEqualTo("/api/public/forms/a/submissions");
        assertThat(body.get("code").asText()).isEqualTo("forbidden_origin");
        assertThat(body.has("properties")).isFalse();
    }

    @Test
    void URI로_옮길_수_없는_경로면_instance_없이_쓴다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetails.write(new MockHttpServletRequest("GET", "/a b|c"), response, ErrorCode.NOT_FOUND);

        JsonNode body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(body.has("instance")).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("not_found");
    }
}
