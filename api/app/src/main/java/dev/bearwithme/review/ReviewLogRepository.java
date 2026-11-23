package dev.bearwithme.review;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    /**
     * FR-R1: new cards introduced since {@code since} (start of today) = cards whose first review log is at or
     * after it. Uses {@code ix_review_logs_user_reviewed} and {@code ix_review_logs_card}.
     */
    @Query("""
            select count(distinct l.cardId) from ReviewLog l
            where l.userId = :userId and l.reviewedAt >= :since
              and not exists (select 1 from ReviewLog p where p.cardId = l.cardId and p.reviewedAt < :since)""")
    long countCardsFirstReviewedSince(long userId, Instant since);
}
