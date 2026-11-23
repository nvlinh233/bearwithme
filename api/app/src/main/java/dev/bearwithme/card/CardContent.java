package dev.bearwithme.card;

import java.util.List;

/** Public API of the card module: what the review screen shows for one card (FR-R2). Tags are sorted. */
public record CardContent(long id, String term, String meaning, String example, String note, List<String> tags) {
}
