package dev.bearwithme.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Table {@code review_logs}: one row per grade (FR-R4). Insert-only. */
@Entity
@Table(name = "review_logs")
class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false, updatable = false)
    private Long cardId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** {@link Grade#button()}: 1 = Again ... 4 = Easy, not the SM-2 quality. */
    @Column(nullable = false, updatable = false)
    private short grade;

    @Column(name = "ease_factor_after", nullable = false, updatable = false, precision = 4, scale = 2)
    private BigDecimal easeFactorAfter;

    @Column(name = "interval_after", nullable = false, updatable = false)
    private int intervalAfter;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected ReviewLog() {
    }

    ReviewLog(long cardId, long userId, Grade grade, Sm2Scheduler.State after, Instant reviewedAt) {
        this.cardId = cardId;
        this.userId = userId;
        this.grade = (short) grade.button();
        this.easeFactorAfter = after.easeFactor();
        this.intervalAfter = after.intervalDays();
        this.reviewedAt = reviewedAt;
    }
}
