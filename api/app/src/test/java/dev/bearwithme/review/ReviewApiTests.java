package dev.bearwithme.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.bearwithme.TestcontainersConfiguration;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

/** FR-R1, FR-R3..R5 through the HTTP API, against PostgreSQL in Testcontainers. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ReviewApiTests {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment env;

    @Autowired
    Clock clock;

    MockMvc mvc;
    String token;
    LocalDate today;

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
        today = LocalDate.now(clock); // FR-D3
    }

    // ---- due queue (FR-R1) ------------------------------------------------------------------

    @Test
    void queueHasOverdueOldestFirstThenNewCardsWithContent() throws Exception {
        long fresh = createCard("rollback", "Undo a deployment", "release");
        long dueYesterday = createCard("hotfix", "Urgent production fix");
        long dueLastWeek = createCard("blocker", "Something that stops progress");
        long dueTomorrow = createCard("nit", "Minor review comment");
        markReviewed(dueYesterday, today.minusDays(1));
        markReviewed(dueLastWeek, today.minusDays(7));
        markReviewed(dueTomorrow, today.plusDays(1));

        due(50)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardId", contains((int) dueLastWeek, (int) dueYesterday, (int) fresh)))
                .andExpect(jsonPath("$[2].term").value("rollback"))
                .andExpect(jsonPath("$[2].meaning").value("Undo a deployment"))
                .andExpect(jsonPath("$[2].tags", contains("release")));
    }

    @Test
    void newCardsAreLimitedTo20PerDayInCreationOrder() throws Exception {
        List<Long> ids = createCards(25);

        due(50)
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].cardId").value(ids.get(0)))
                .andExpect(jsonPath("$[19].cardId").value(ids.get(19)));
    }

    @Test
    void newCardsFirstReviewedTodayUseUpTheQuotaButOlderCardsDoNot() throws Exception {
        List<Long> ids = createCards(23);
        long oldCard = ids.get(0); // first reviewed two days ago, due today
        markReviewed(oldCard, today);
        jdbc.update("INSERT INTO review_logs (card_id, user_id, grade, ease_factor_after, interval_after, reviewed_at)"
                + " SELECT card_id, user_id, 3, 2.50, 1, ? FROM review_states WHERE card_id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(2))), oldCard);

        due(50).andExpect(jsonPath("$", hasSize(1 + 20)));

        grade(oldCard, "GOOD").andExpect(status().isOk());
        grade(ids.get(1), "GOOD").andExpect(status().isOk());
        grade(ids.get(2), "AGAIN").andExpect(status().isOk()); // still a new card introduced today

        // 0 due (all graded cards are due later) + 20 - 2 new cards
        due(50)
                .andExpect(jsonPath("$", hasSize(18)))
                .andExpect(jsonPath("$[0].cardId").value(ids.get(3)));
    }

    @Test
    void limitCapsTheSessionAndMustBeAtMost50() throws Exception {
        createCards(8);

        due(5).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)));
        due(51).andExpect(status().isBadRequest());
        due(0).andExpect(status().isBadRequest());
    }

    @Test
    void queueRequiresToken() throws Exception {
        mvc.perform(get("/api/reviews/due")).andExpect(status().isUnauthorized());
    }

    // ---- grade (FR-R3, FR-R4) ---------------------------------------------------------------

    @Test
    void gradeGoodOnNewCardUpdatesStateAndWritesLog() throws Exception {
        long id = createCard("deploy", "Release to an environment");

        grade(id, "GOOD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDueDate").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.intervalDays").value(1))
                .andExpect(jsonPath("$.easeFactor").value(2.5));

        Map<String, Object> state = jdbc.queryForMap("SELECT ease_factor, interval_days, repetitions, due_date,"
                + " last_reviewed_at FROM review_states WHERE card_id = ?", id);
        assertThat(state.get("ease_factor").toString()).isEqualTo("2.50");
        assertThat(state.get("interval_days")).isEqualTo(1);
        assertThat(state.get("repetitions")).isEqualTo(1);
        assertThat(((Date) state.get("due_date")).toLocalDate()).isEqualTo(today.plusDays(1));
        assertThat(state.get("last_reviewed_at")).isNotNull();

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT grade, ease_factor_after, interval_after FROM review_logs WHERE card_id = ?", id);
        assertThat(((Number) log.get("grade")).intValue()).isEqualTo(3); // button, not quality
        assertThat(log.get("ease_factor_after").toString()).isEqualTo("2.50");
        assertThat(log.get("interval_after")).isEqualTo(1);
    }

    @Test
    void secondGoodGivesSixDaysAndAgainResets() throws Exception {
        long id = createCard("standup", "Short daily meeting");
        grade(id, "GOOD");

        grade(id, "GOOD").andExpect(jsonPath("$.intervalDays").value(6))
                .andExpect(jsonPath("$.nextDueDate").value(today.plusDays(6).toString()));

        // EF 2.50 + 0.1 - 4 * (0.08 + 0.02 * 4) = 1.96
        grade(id, "AGAIN")
                .andExpect(jsonPath("$.intervalDays").value(1))
                .andExpect(jsonPath("$.easeFactor").value(1.96));

        assertThat(jdbc.queryForObject("SELECT repetitions FROM review_states WHERE card_id = ?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForList("SELECT grade FROM review_logs WHERE card_id = ? ORDER BY id", Integer.class, id))
                .containsExactly(3, 3, 1);
    }

    @Test
    void gradeUnknownCardIs404() throws Exception {
        grade(999_999, "GOOD").andExpect(status().isNotFound());
    }

    @Test
    void invalidOrMissingGradeIs400AndWritesNothing() throws Exception {
        long id = createCard("merge", "Combine branches");

        grade(id, "MEH").andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/reviews/" + id)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("grade"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_logs", Long.class)).isZero();
    }

    // ---- helpers ----------------------------------------------------------------------------

    private ResultActions due(int limit) throws Exception {
        return mvc.perform(auth(get("/api/reviews/due").param("limit", String.valueOf(limit))));
    }

    private ResultActions grade(long cardId, String grade) throws Exception {
        return mvc.perform(auth(post("/api/reviews/" + cardId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grade\":\"" + grade + "\"}"));
    }

    private List<Long> createCards(int count) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(createCard("term " + i, "meaning " + i));
        }
        return ids;
    }

    private long createCard(String term, String meaning, String... tags) throws Exception {
        String tagJson = tags.length == 0 ? "[]" : "[\"" + String.join("\",\"", tags) + "\"]";
        String json = "{\"term\":\"%s\",\"meaning\":\"%s\",\"tags\":%s}".formatted(term, meaning, tagJson);
        String body = mvc.perform(auth(post("/api/cards")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Turns a new card into a reviewed one due on {@code dueDate}. */
    private void markReviewed(long cardId, LocalDate dueDate) {
        jdbc.update("UPDATE review_states SET repetitions = 1, interval_days = 1, due_date = ?,"
                + " last_reviewed_at = now() WHERE card_id = ?", Date.valueOf(dueDate), cardId);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
