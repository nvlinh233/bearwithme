package dev.bearwithme.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.bearwithme.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** FR-I1..I4 against PostgreSQL in Testcontainers. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CardImportTests {

    private static final String HEADER = "term,meaning,example,tags\n";

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
        jdbc.update("DELETE FROM cards");
        jdbc.update("DELETE FROM tags");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String login = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(
                env.getRequiredProperty("BWM_SEED_USERNAME"), env.getRequiredProperty("BWM_SEED_PASSWORD"));
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.accessToken");
    }

    @Test
    void importsValidRowsWithQuotesTagsAndReviewStates() throws Exception {
        String csv = HEADER
                + "LGTM,\"Looks good to me, approve\",\"He said \"\"LGTM\"\" and merged\",code-review|slang\n"
                + "Blocker,Something that stops progress,,standup\n"
                + "Spike,\"Short research task\",\"Line one\nline two\",\n";

        upload(csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(count("SELECT count(*) FROM cards")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM review_states WHERE due_date IS NULL")).isEqualTo(3); // FR-C5
        assertThat(jdbc.queryForObject("SELECT example FROM cards WHERE term = 'LGTM'", String.class))
                .isEqualTo("He said \"LGTM\" and merged");
        assertThat(jdbc.queryForObject("SELECT example FROM cards WHERE term = 'Spike'", String.class))
                .isEqualTo("Line one\nline two");
        assertThat(count("SELECT count(*) FROM card_tags")).isEqualTo(3);
    }

    @Test
    void skipsExistingTermsAndDuplicatesWithinTheFile() throws Exception {
        upload(HEADER + "Deploy,Release to an environment,,\n").andExpect(jsonPath("$.created").value(1));

        upload(HEADER
                + "  DEPLOY ,Existing term in another case,,\n"
                + "Canary,Release to a small group first,,\n"
                + "canary,Same term again in this file,,\n")
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    void reportsInvalidRowsWithLineNumbersAndSavesTheRest() throws Exception {
        String csv = HEADER                                   // line 1
                + "Rollout,Gradual release,,\n"               // line 2: valid
                + "Hotfix,,,\n"                               // line 3: meaning blank
                + "Retro,Look back on the sprint,,Team Event\n" // line 4: tag not kebab-case
                + "Standup,Daily meeting\n"                   // line 5: 2 columns
                + "Backlog,Ordered list of work,,\n";         // line 6: valid

        upload(csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].line").value(3))
                .andExpect(jsonPath("$.errors[1].line").value(4))
                .andExpect(jsonPath("$.errors[2].line").value(5))
                .andExpect(jsonPath("$.errors[2].reason").value("expected 4 columns but found 2"));
    }

    @Test
    void wrongHeaderIs400() throws Exception {
        upload("word,definition\nx,y\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Line 1: expected header term,meaning,example,tags but found word,definition"));
    }

    @Test
    void moreThan2000RowsIs400AndSavesNothing() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i <= CardImportService.MAX_ROWS; i++) {
            csv.append("term-").append(i).append(",meaning,,\n");
        }

        upload(csv.toString()).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM cards")).isZero();
    }

    @Test
    void invalidUtf8Is400() throws Exception {
        byte[] latin1 = (HEADER + "Café,Coffee shop,,\n").getBytes(StandardCharsets.ISO_8859_1);

        upload(latin1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("File is not valid UTF-8"));
    }

    @Test
    void unclosedQuoteIs400WithLine() throws Exception {
        upload(HEADER + "Ok,fine,,\n\"Broken,meaning,,\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Line 3: unclosed quote"));
    }

    @Test
    void missingFileIs400() throws Exception {
        mvc.perform(multipart("/api/cards/import").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private ResultActions upload(String csv) throws Exception {
        return upload(csv.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions upload(byte[] content) throws Exception {
        var file = new MockMultipartFile("file", "cards.csv", "text/csv", content);
        return mvc.perform(multipart("/api/cards/import").file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
