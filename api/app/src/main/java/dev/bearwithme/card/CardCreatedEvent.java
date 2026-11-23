package dev.bearwithme.card;

/**
 * Published inside the transaction that creates a card. The review module listens and creates the card's
 * review state (FR-C5), so the card module does not depend on the review module.
 */
public record CardCreatedEvent(long cardId, long userId) {
}
