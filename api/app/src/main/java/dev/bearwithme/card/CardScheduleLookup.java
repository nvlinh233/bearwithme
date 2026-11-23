package dev.bearwithme.card;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Next due date of cards, implemented by the review module (dependency inversion: review depends on card,
 * never the other way). A card missing from the result, or mapped to {@code null}, is new.
 */
public interface CardScheduleLookup {

    Map<Long, LocalDate> dueDates(Collection<Long> cardIds);
}
