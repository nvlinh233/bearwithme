package dev.bearwithme.card;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the card module: card content for other modules (the review queue, FR-R1), so they never
 * query {@code cards} or {@code tags} directly.
 */
@Component
public class CardContentLookup {

    private final CardRepository cards;

    CardContentLookup(CardRepository cards) {
        this.cards = cards;
    }

    /** Cards of {@code userId} among {@code cardIds}; unknown ids are absent from the result. */
    @Transactional(readOnly = true)
    public Map<Long, CardContent> find(long userId, Collection<Long> cardIds) {
        Map<Long, CardContent> result = new HashMap<>();
        if (cardIds.isEmpty()) {
            return result;
        }
        for (Card c : cards.findByUserIdAndIdIn(userId, cardIds)) { // tags: @BatchSize, one extra query
            result.put(c.getId(), new CardContent(c.getId(), c.getTerm(), c.getMeaning(), c.getExample(),
                    c.getNote(), c.getTags().stream().map(Tag::getName).sorted().toList()));
        }
        return result;
    }
}
