package dev.bearwithme.review;

/**
 * The four review buttons (FR-R3). {@link #button()} is the value stored in {@code review_logs.grade} (1-4);
 * {@link #quality()} is the SM-2 quality q used by {@link Sm2Scheduler} (spec Scheduling rules).
 */
enum Grade {
    AGAIN(1, 1),
    HARD(2, 3),
    GOOD(3, 4),
    EASY(4, 5);

    private final int button;
    private final int quality;

    Grade(int button, int quality) {
        this.button = button;
        this.quality = quality;
    }

    int button() {
        return button;
    }

    int quality() {
        return quality;
    }
}
