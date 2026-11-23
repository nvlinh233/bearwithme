package dev.bearwithme.review;

import dev.bearwithme.card.CardScheduleLookup;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Due dates for the card module's list and detail views. */
@Component
class ReviewScheduleLookup implements CardScheduleLookup {

    private final ReviewStateRepository states;

    ReviewScheduleLookup(ReviewStateRepository states) {
        this.states = states;
    }

    /** Runs in the caller's transaction (CardService). */
    @Override
    public Map<Long, LocalDate> dueDates(Collection<Long> cardIds) {
        Map<Long, LocalDate> result = new HashMap<>(); // HashMap: values may be null (new cards)
        if (cardIds.isEmpty()) {
            return result;
        }
        for (ReviewState state : states.findAllById(cardIds)) {
            result.put(state.getId(), state.getDueDate());
        }
        return result;
    }
}
