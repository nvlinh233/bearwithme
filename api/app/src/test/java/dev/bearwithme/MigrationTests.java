package dev.bearwithme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MigrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment env;

    @Test
    void createsAllTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains(
                "users", "cards", "tags", "card_tags", "review_states", "review_logs");
    }

    @Test
    void seedsSingleUserWithBcryptPassword() {
        // FR-A1
        var user = jdbc.queryForMap("SELECT username, password_hash FROM users");

        assertThat(user.get("username")).isEqualTo(env.getRequiredProperty("BWM_SEED_USERNAME"));
        String hash = (String) user.get("password_hash");
        assertThat(hash).startsWith("$2a$10$");
        assertThat(new BCryptPasswordEncoder().matches(env.getRequiredProperty("BWM_SEED_PASSWORD"), hash)).isTrue();
    }
}
