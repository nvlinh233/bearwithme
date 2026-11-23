package dev.bearwithme.card;

/** Response item of {@code GET /api/tags}. */
public record TagCount(String name, Long cardCount) {
}
