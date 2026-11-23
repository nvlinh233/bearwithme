package dev.bearwithme.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Classic SM-2 (spec Scheduling rules, FR-R3). Pure and stateless: no Spring, no clock; the caller passes
 * {@code today} in Asia/Ho_Chi_Minh (FR-D3).
 */
final class Sm2Scheduler {

    static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");

    private static final BigDecimal BASE_GAIN = new BigDecimal("0.10");
    private static final BigDecimal LINEAR = new BigDecimal("0.08");
    private static final BigDecimal QUADRATIC = new BigDecimal("0.02");

    private Sm2Scheduler() {
    }

    /** SM-2 state of one card before or after a grade. */
    record State(BigDecimal easeFactor, int repetitions, int intervalDays) {

        State {
            Objects.requireNonNull(easeFactor, "easeFactor");
            if (repetitions < 0 || intervalDays < 0) {
                throw new IllegalArgumentException("repetitions and intervalDays must be >= 0");
            }
        }

        /** New card: EF = 2.5, n = 0, I = 0. */
        static State initial() {
            return new State(ReviewState.INITIAL_EASE_FACTOR, 0, 0);
        }
    }

    record Result(State state, LocalDate dueDate) {
    }

    static Result schedule(State current, Grade grade, LocalDate today) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(today, "today");

        int q = grade.quality();
        BigDecimal easeFactor = nextEaseFactor(current.easeFactor(), q);

        int repetitions;
        int interval;
        if (q < 3) {
            // Again: start over; the session re-queues the card (FR-R5).
            repetitions = 0;
            interval = 1;
        } else {
            repetitions = current.repetitions() + 1;
            interval = switch (repetitions) {
                case 1 -> 1;
                case 2 -> 6;
                default -> BigDecimal.valueOf(current.intervalDays())
                        .multiply(easeFactor)
                        .setScale(0, RoundingMode.HALF_UP)
                        .intValueExact();
            };
        }
        return new Result(new State(easeFactor, repetitions, interval), today.plusDays(interval));
    }

    /** EF' = max(1.3, EF + 0.1 - (5 - q)(0.08 + 0.02(5 - q))), kept at 2 decimals like {@code numeric(4,2)}. */
    static BigDecimal nextEaseFactor(BigDecimal easeFactor, int q) {
        BigDecimal miss = BigDecimal.valueOf(5 - q);
        BigDecimal penalty = miss.multiply(LINEAR.add(QUADRATIC.multiply(miss)));
        BigDecimal next = easeFactor.add(BASE_GAIN).subtract(penalty).setScale(2, RoundingMode.HALF_UP);
        return next.max(MIN_EASE_FACTOR);
    }
}
