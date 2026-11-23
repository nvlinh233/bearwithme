package dev.bearwithme.review;

import dev.bearwithme.card.CardCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to card module events. {@code @EventListener} runs synchronously in the publisher's transaction, so
 * the card and its review state are committed (or rolled back) together.
 */
@Component
class CardLifecycleListener {

    private final ReviewStateRepository states;

    CardLifecycleListener(ReviewStateRepository states) {
        this.states = states;
    }

    @EventListener
    void onCardCreated(CardCreatedEvent event) {
        states.save(ReviewState.forNewCard(event.cardId(), event.userId()));
    }
}
