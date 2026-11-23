package dev.bearwithme.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bearwithme.review.Sm2Scheduler.Result;
import dev.bearwithme.review.Sm2Scheduler.State;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Sm2SchedulerTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private static State state(String easeFactor, int repetitions, int intervalDays) {
        return new State(new BigDecimal(easeFactor), repetitions, intervalDays);
    }

    @ParameterizedTest
    @CsvSource({
            "AGAIN, 1, 1",
            "HARD,  2, 3",
            "GOOD,  3, 4",
            "EASY,  4, 5",
    })
    void gradeMapsButtonAndQuality(Grade grade, int button, int quality) {
        assertThat(grade.button()).isEqualTo(button);
        assertThat(grade.quality()).isEqualTo(quality);
    }

    @ParameterizedTest
    @CsvSource({
            "AGAIN, 1.96",
            "HARD,  2.36",
            "GOOD,  2.50",
            "EASY,  2.60",
    })
    void easeFactorUpdateFromInitial(Grade grade, String expected) {
        Result result = Sm2Scheduler.schedule(State.initial(), grade, TODAY);

        assertThat(result.state().easeFactor()).isEqualByComparingTo(expected);
        assertThat(result.state().easeFactor().scale()).isEqualTo(2);
    }

    @Test
    void repeatedGoodGives1_6_15_38() {
        State current = State.initial();
        List<Integer> intervals = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            current = Sm2Scheduler.schedule(current, Grade.GOOD, TODAY).state();
            intervals.add(current.intervalDays());
        }

        assertThat(intervals).containsExactly(1, 6, 15, 38);
        assertThat(current.repetitions()).isEqualTo(4);
        assertThat(current.easeFactor()).isEqualByComparingTo("2.50");
    }

    @Test
    void firstSuccessfulReviewIsOneDay() {
        Result result = Sm2Scheduler.schedule(State.initial(), Grade.EASY, TODAY);

        assertThat(result.state().repetitions()).isEqualTo(1);
        assertThat(result.state().intervalDays()).isEqualTo(1);
        assertThat(result.dueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void secondSuccessfulReviewIsSixDays() {
        Result result = Sm2Scheduler.schedule(state("2.50", 1, 1), Grade.HARD, TODAY);

        assertThat(result.state().repetitions()).isEqualTo(2);
        assertThat(result.state().intervalDays()).isEqualTo(6);
        assertThat(result.dueDate()).isEqualTo(TODAY.plusDays(6));
    }

    @Test
    void laterIntervalsUseUpdatedEaseFactor() {
        // EF' = 2.50 + 0.10 = 2.60; I = round(6 x 2.60) = round(15.6) = 16
        Result result = Sm2Scheduler.schedule(state("2.50", 2, 6), Grade.EASY, TODAY);

        assertThat(result.state().easeFactor()).isEqualByComparingTo("2.60");
        assertThat(result.state().repetitions()).isEqualTo(3);
        assertThat(result.state().intervalDays()).isEqualTo(16);
        assertThat(result.dueDate()).isEqualTo(TODAY.plusDays(16));
    }

    @Test
    void intervalRoundsHalfUp() {
        // GOOD keeps EF at 2.50: 15 x 2.50 = 37.5 -> 38, 3 x 2.50 = 7.5 -> 8
        assertThat(Sm2Scheduler.schedule(state("2.50", 3, 15), Grade.GOOD, TODAY).state().intervalDays())
                .isEqualTo(38);
        assertThat(Sm2Scheduler.schedule(state("2.50", 3, 3), Grade.GOOD, TODAY).state().intervalDays())
                .isEqualTo(8);
    }

    @Test
    void againResetsRepetitionsAndIntervalButStillLowersEaseFactor() {
        Result result = Sm2Scheduler.schedule(state("2.50", 5, 38), Grade.AGAIN, TODAY);

        assertThat(result.state().repetitions()).isZero();
        assertThat(result.state().intervalDays()).isEqualTo(1);
        assertThat(result.state().easeFactor()).isEqualByComparingTo("1.96");
        assertThat(result.dueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void goodAfterAgainRestartsAtOneDay() {
        State afterAgain = Sm2Scheduler.schedule(state("2.50", 5, 38), Grade.AGAIN, TODAY).state();

        State next = Sm2Scheduler.schedule(afterAgain, Grade.GOOD, TODAY).state();

        assertThat(next.repetitions()).isEqualTo(1);
        assertThat(next.intervalDays()).isEqualTo(1);
    }

    @Test
    void easeFactorNeverDropsBelowFloor() {
        // 1.50 - 0.54 = 0.96 -> 1.30
        assertThat(Sm2Scheduler.nextEaseFactor(new BigDecimal("1.50"), Grade.AGAIN.quality()))
                .isEqualByComparingTo("1.30");
        // 1.30 - 0.14 = 1.16 -> 1.30
        assertThat(Sm2Scheduler.nextEaseFactor(new BigDecimal("1.30"), Grade.HARD.quality()))
                .isEqualByComparingTo("1.30");
        // just above the floor stays: 1.44 - 0.14 = 1.30
        assertThat(Sm2Scheduler.nextEaseFactor(new BigDecimal("1.44"), Grade.HARD.quality()))
                .isEqualByComparingTo("1.30");
    }

    @Test
    void repeatedAgainSettlesAtFloor() {
        State current = State.initial();
        for (int i = 0; i < 5; i++) {
            current = Sm2Scheduler.schedule(current, Grade.AGAIN, TODAY).state();
        }

        assertThat(current.easeFactor()).isEqualByComparingTo(Sm2Scheduler.MIN_EASE_FACTOR);
    }

    @Test
    void easyHasNoUpperBound() {
        assertThat(Sm2Scheduler.nextEaseFactor(new BigDecimal("3.00"), Grade.EASY.quality()))
                .isEqualByComparingTo("3.10");
    }

    @Test
    void dueDateCrossesMonthAndYear() {
        LocalDate lateDecember = LocalDate.of(2026, 12, 30);

        Result result = Sm2Scheduler.schedule(state("2.50", 1, 1), Grade.GOOD, lateDecember);

        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> state("2.50", -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state("2.50", 0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new State(null, 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Sm2Scheduler.schedule(State.initial(), null, TODAY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Sm2Scheduler.schedule(State.initial(), Grade.GOOD, null))
                .isInstanceOf(NullPointerException.class);
    }
}
