package dev.bearwithme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** FR-A1..A3: login, token validation and 401 responses. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AuthTests {

    /** Any protected path works: an authenticated request without a handler gets 404, not 401. */
    private static final String PROTECTED = "/api/cards";

    @Autowired
    WebApplicationContext context;

    @Autowired
    Environment env;

    MockMvc mvc;
    String username;
    String password;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        username = env.getRequiredProperty("BWM_SEED_USERNAME");
        password = env.getRequiredProperty("BWM_SEED_PASSWORD");
    }

    @Test
    void loginReturnsTokenValidForSevenDays() throws Exception {
        Instant before = Instant.now();

        String body = login(username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(expiresAt).isBetween(
                before.plus(Duration.ofDays(7)).minusSeconds(5),
                Instant.now().plus(Duration.ofDays(7)).plusSeconds(5));
    }

    @Test
    void tokenFromLoginGrantsAccess() throws Exception {
        String body = login(username, password).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        login(username, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void unknownUserGetsSameResponseAsWrongPassword() throws Exception {
        login("nobody", password)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void blankCredentialsAreBadRequest() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void missingTokenIsUnauthorizedProblem() throws Exception {
        mvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        // beyond the decoder's 60 s clock skew
        Instant past = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
        String token = sign(secret(), issuer(), past.minus(Duration.ofDays(7)), past);

        assertRejected(token);
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() throws Exception {
        String otherSecret = "x".repeat(64);
        assertRejected(sign(otherSecret, issuer(), Instant.now(), Instant.now().plus(Duration.ofDays(1))));
    }

    @Test
    void tokenFromOtherIssuerIsRejected() throws Exception {
        assertRejected(sign(secret(), "someone-else", Instant.now(), Instant.now().plus(Duration.ofDays(1))));
    }

    private ResultActions login(String user, String pass) throws Exception {
        String json = "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertRejected(String token) throws Exception {
        mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String secret() {
        return env.getRequiredProperty("BWM_JWT_SECRET");
    }

    private String issuer() {
        return env.getRequiredProperty("BWM_JWT_ISSUER");
    }

    private static String sign(String secret, String issuer, Instant issuedAt, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("nvlinh233")
                .issuedAt(issuedAt.truncatedTo(ChronoUnit.SECONDS))
                .expiresAt(expiresAt.truncatedTo(ChronoUnit.SECONDS))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
