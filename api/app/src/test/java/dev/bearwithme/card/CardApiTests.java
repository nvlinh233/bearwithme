package dev.bearwithme.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.bearwithme.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** FR-C1..C6 through the HTTP API, against PostgreSQL in Testcontainers. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CardApiTests {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment env;

    MockMvc mvc;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        // review_states, review_logs and card_tags go with cards (ON DELETE CASCADE)
        jdbc.update("DELETE FROM cards");
        jdbc.update("DELETE FROM tags");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String login = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(
                env.getRequiredProperty("BWM_SEED_USERNAME"), env.getRequiredProperty("BWM_SEED_PASSWORD"));
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.accessToken");
    }

    // ---- create -------------------------------------------------------------------------------

    @Test
    void createReturns201WithLocationAndNewReviewState() throws Exception {
        String body = create(card("  code review  ", "Checking code before merge", "code-review", "process"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.term").value("code review")) // trimmed (FR-C2)
                .andExpect(jsonPath("$.tags", contains("code-review", "process")))
                .andExpect(jsonPath("$.dueDate").value(nullValue())) // new card (FR-C5)
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        Map<String, Object> state = jdbc.queryForMap(
                "SELECT ease_factor, interval_days, repetitions, due_date FROM review_states WHERE card_id = ?", id);
        assertThat(state.get("ease_factor").toString()).isEqualTo("2.50");
        assertThat(state.get("interval_days")).isEqualTo(0);
        assertThat(state.get("repetitions")).isEqualTo(0);
        assertThat(state.get("due_date")).isNull();
    }

    @Test
    void duplicateTermIgnoringCaseAndSpacesIs409() throws Exception {
        create(card("Deploy", "Release to an environment")).andExpect(status().isCreated());

        create(card("  dEPLOY ", "Another meaning")).andExpect(status().isConflict());
    }

    @Test
    void invalidCardIs400WithFieldErrors() throws Exception {
        String tooLong = "x".repeat(201);
        create(card(tooLong, "")) // term too long, meaning blank
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("term")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("meaning")));
    }

    @Test
    void tagsMustBeKebabCaseAndAtMostTen() throws Exception {
        create(card("Standup", "Short daily meeting", "Daily Meeting")).andExpect(status().isBadRequest());

        String[] eleven = new String[11];
        for (int i = 0; i < eleven.length; i++) {
            eleven[i] = "tag-" + i;
        }
        create(card("Retro", "Look back on the sprint", eleven)).andExpect(status().isBadRequest());
    }

    // ---- read ---------------------------------------------------------------------------------

    @Test
    void getUnknownCardIs404() throws Exception {
        mvc.perform(auth(get("/api/cards/999999"))).andExpect(status().isNotFound());
    }

    @Test
    void listSearchesTermAndMeaningFiltersByTagAndPages() throws Exception {
        create(card("Merge conflict", "Two branches changed the same lines", "git")).andExpect(status().isCreated());
        create(card("Rebase", "Replay commits on top of another branch", "git")).andExpect(status().isCreated());
        create(card("Blocker", "Something that stops progress", "standup")).andExpect(status().isCreated());

        // q matches meaning, case-insensitive
        mvc.perform(auth(get("/api/cards").param("q", "BRANCH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // tag filter + sort by term
        mvc.perform(auth(get("/api/cards").param("tag", "git").param("sort", "term")))
                .andExpect(jsonPath("$.content[*].term", contains("Merge conflict", "Rebase")));

        // paging
        mvc.perform(auth(get("/api/cards").param("size", "2").param("page", "1")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void likeWildcardsInQueryAreLiteral() throws Exception {
        create(card("Uptime", "Time the service is up")).andExpect(status().isCreated());

        mvc.perform(auth(get("/api/cards").param("q", "%")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidListParametersAre400() throws Exception {
        mvc.perform(auth(get("/api/cards").param("size", "1000"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/cards").param("sort", "random"))).andExpect(status().isBadRequest());
    }

    // ---- update -------------------------------------------------------------------------------

    @Test
    void updateChangesFieldsAndTags() throws Exception {
        long id = createdId(card("Hotfix", "Urgent fix", "release"));

        mvc.perform(auth(put("/api/cards/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(card("hotfix", "Urgent production fix", "incident")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term").value("hotfix")) // changing only the case of its own term is fine
                .andExpect(jsonPath("$.meaning").value("Urgent production fix"))
                .andExpect(jsonPath("$.tags", contains("incident")));
    }

    @Test
    void updateToAnotherCardsTermIs409() throws Exception {
        createdId(card("Sprint", "Fixed-length iteration"));
        long id = createdId(card("Backlog", "Ordered list of work"));

        mvc.perform(auth(put("/api/cards/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(card("SPRINT", "Fixed-length iteration")))
                .andExpect(status().isConflict());
    }

    // ---- delete -------------------------------------------------------------------------------

    @Test
    void deleteRemovesCardAndReviewState() throws Exception {
        long id = createdId(card("Rollback", "Return to the previous version"));

        mvc.perform(auth(delete("/api/cards/" + id))).andExpect(status().isNoContent());

        mvc.perform(auth(get("/api/cards/" + id))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_states WHERE card_id = ?", Long.class, id))
                .isZero();
    }

    // ---- tags ---------------------------------------------------------------------------------

    @Test
    void tagsAreReusedAndCounted() throws Exception {
        createdId(card("Pull request", "Request to merge a branch", "git", "code-review"));
        createdId(card("Squash", "Combine commits into one", "git"));

        mvc.perform(auth(get("/api/tags")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("code-review", "git")))
                .andExpect(jsonPath("$[1].cardCount").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE name = 'git'", Long.class)).isEqualTo(1);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/cards")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(auth(post("/api/cards")).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createdId(String json) throws Exception {
        String body = create(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String card(String term, String meaning, String... tags) {
        StringBuilder json = new StringBuilder()
                .append("{\"term\":\"").append(term).append("\",\"meaning\":\"").append(meaning).append("\",\"tags\":[");
        for (int i = 0; i < tags.length; i++) {
            json.append(i == 0 ? "" : ",").append('"').append(tags[i]).append('"');
        }
        return json.append("]}").toString();
    }
}
