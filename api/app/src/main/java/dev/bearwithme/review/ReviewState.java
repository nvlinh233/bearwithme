package dev.bearwithme.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.domain.Persistable;

/**
 * Table {@code review_states}: SM-2 state of one card. The id is the card id (assigned, not generated), so it
 * implements {@link Persistable}: otherwise {@code save()} would see a non-null id, assume an existing row and
 * run a SELECT + merge instead of a plain INSERT.
 */
@Entity
@Table(name = "review_states")
class ReviewState implements Persistable<Long> {

    static final BigDecimal INITIAL_EASE_FACTOR = new BigDecimal("2.50");

    @Id
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private int repetitions;

    /** {@code null} while the card is new (never reviewed). */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Transient
    private boolean isNew = true;

    protected ReviewState() {
    }

    /** FR-C5 and spec Scheduling rules: EF = 2.5, n = 0, I = 0. */
    static ReviewState forNewCard(long cardId, long userId) {
        ReviewState state = new ReviewState();
        state.cardId = cardId;
        state.userId = userId;
        state.easeFactor = INITIAL_EASE_FACTOR;
        state.intervalDays = 0;
        state.repetitions = 0;
        return state;
    }

    @Override
    public Long getId() {
        return cardId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    Sm2Scheduler.State toSchedulerState() {
        return new Sm2Scheduler.State(easeFactor, repetitions, intervalDays);
    }

    /** FR-R3: stores the scheduler's result; dirty checking writes the UPDATE at commit. */
    void apply(Sm2Scheduler.Result result, Instant reviewedAt) {
        Sm2Scheduler.State next = result.state();
        this.easeFactor = next.easeFactor();
        this.repetitions = next.repetitions();
        this.intervalDays = next.intervalDays();
        this.dueDate = result.dueDate();
        this.lastReviewedAt = reviewedAt;
    }
}
