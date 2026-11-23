package dev.bearwithme.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Table {@code tags}. Name is lowercase kebab-case (FR-C3), unique per user. */
@Entity
@Table(name = "tags")
class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    protected Tag() {
    }

    Tag(long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    String getName() {
        return name;
    }
}
