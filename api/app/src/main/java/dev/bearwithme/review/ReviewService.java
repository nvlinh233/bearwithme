package dev.bearwithme.review;

import dev.bearwithme.auth.CurrentUser;
import dev.bearwithme.card.CardContent;
import dev.bearwithme.card.CardContentLookup;
import dev.bearwithme.common.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-R1, FR-R3..R5. Day boundaries come from the injected {@link Clock} (Asia/Ho_Chi_Minh, FR-D3). */
@Service
@Transactional
class ReviewService {

    static final int NEW_CARDS_PER_DAY = 20;
    static final int MAX_SESSION_SIZE = 50;

    private final ReviewStateRepository states;
    private final ReviewLogRepository logs;
    private final CardContentLookup cards;
    private final CurrentUser currentUser;
    private final Clock clock;

    ReviewService(ReviewStateRepository states, ReviewLogRepository logs, CardContentLookup cards,
            CurrentUser currentUser, Clock clock) {
        this.states = states;
        this.logs = logs;
        this.cards = cards;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    record DueCard(long cardId, String term, String meaning, String example, String note, List<String> tags) {

        static DueCard of(CardContent c) {
            return new DueCard(c.id(), c.term(), c.meaning(), c.example(), c.note(), c.tags());
        }
    }

    record GradeResult(LocalDate nextDueDate, int intervalDays, BigDecimal easeFactor) {
    }

    /**
     * FR-R1: due reviewed cards (oldest due first), then new cards in creation order while today's new-card
     * quota lasts, at most {@code limit} in total. Cards graded Again are due tomorrow, so re-showing them in
     * the current session (FR-R5) is the client's job.
     */
    @Transactional(readOnly = true)
    List<DueCard> dueCards(int limit) {
        long userId = currentUser.id();
        LocalDate today = LocalDate.now(clock);
        Instant startOfToday = today.atStartOfDay(clock.getZone()).toInstant();

        List<Long> ids = new ArrayList<>(states.findDueCardIds(userId, today, Limit.of(limit)));
        long introducedToday = logs.countCardsFirstReviewedSince(userId, startOfToday);
        int newQuota = (int) Math.min(limit - ids.size(), Math.max(0, NEW_CARDS_PER_DAY - introducedToday));
        if (newQuota > 0) {
            ids.addAll(states.findNewCardIds(userId, Limit.of(newQuota)));
        }

        Map<Long, CardContent> content = cards.find(userId, ids);
        return ids.stream().map(content::get).filter(Objects::nonNull).map(DueCard::of).toList();
    }

    /** FR-R3, FR-R4: updates the review state and writes one review log in this one transaction. */
    GradeResult grade(long cardId, Grade grade) {
        long userId = currentUser.id();
        ReviewState state = states.findByCardIdAndUserId(cardId, userId)
                .orElseThrow(() -> new NotFoundException("Card " + cardId + " not found"));
        Instant now = clock.instant();

        Sm2Scheduler.Result result = Sm2Scheduler.schedule(state.toSchedulerState(), grade, LocalDate.now(clock));
        state.apply(result, now);
        logs.save(new ReviewLog(cardId, userId, grade, result.state(), now));

        Sm2Scheduler.State next = result.state();
        return new GradeResult(result.dueDate(), next.intervalDays(), next.easeFactor());
    }
}
