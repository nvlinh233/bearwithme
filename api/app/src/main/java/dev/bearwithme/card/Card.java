package dev.bearwithme.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * Table {@code cards} (FR-C1). {@code userId} is a plain column, not an association: users belong to the
 * auth module.
 */
@Entity
@Table(name = "cards")
class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String term;

    @Column(nullable = false, length = 500)
    private String meaning;

    @Column(length = 1000)
    private String example;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Loads the tags of a whole page in one extra query instead of one per card (N+1)
    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "card_tags",
            joinColumns = @JoinColumn(name = "card_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    protected Card() {
    }

    Card(long userId, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String term, String meaning, String example, String note, Set<Tag> tags, Instant now) {
        this.term = term;
        this.meaning = meaning;
        this.example = example;
        this.note = note;
        this.tags.clear();
        this.tags.addAll(tags);
        this.updatedAt = now;
    }

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    String getTerm() {
        return term;
    }

    String getMeaning() {
        return meaning;
    }

    String getExample() {
        return example;
    }

    String getNote() {
        return note;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Set<Tag> getTags() {
        return tags;
    }
}
