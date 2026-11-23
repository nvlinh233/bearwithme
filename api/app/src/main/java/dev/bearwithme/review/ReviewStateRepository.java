package dev.bearwithme.review;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ReviewStateRepository extends JpaRepository<ReviewState, Long> {

    /** FR-R1: reviewed cards due today or earlier, oldest due first ({@code ix_review_states_user_due}). */
    @Query("select s.cardId from ReviewState s where s.userId = :userId and s.dueDate <= :today"
            + " order by s.dueDate, s.cardId")
    List<Long> findDueCardIds(long userId, LocalDate today, Limit limit);

    /** FR-C5, FR-R1: new cards in creation order; card ids are IDENTITY, so id order is creation order. */
    @Query("select s.cardId from ReviewState s where s.userId = :userId and s.dueDate is null order by s.cardId")
    List<Long> findNewCardIds(long userId, Limit limit);

    /** FR-R4: row lock (SELECT ... FOR UPDATE) so two concurrent grades of one card apply one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReviewState> findByCardIdAndUserId(Long cardId, Long userId);
}
