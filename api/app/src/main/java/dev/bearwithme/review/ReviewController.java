package dev.bearwithme.review;

import dev.bearwithme.review.ReviewService.DueCard;
import dev.bearwithme.review.ReviewService.GradeResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
class ReviewController {

    private final ReviewService service;

    ReviewController(ReviewService service) {
        this.service = service;
    }

    /** FR-R3: AGAIN, HARD, GOOD or EASY; anything else is 400. */
    record GradeRequest(@NotNull Grade grade) {
    }

    /** FR-R1: one session shows at most 50 cards. */
    @GetMapping("/due")
    List<DueCard> due(@RequestParam(defaultValue = "50") @Min(1) @Max(ReviewService.MAX_SESSION_SIZE) int limit) {
        return service.dueCards(limit);
    }

    /** FR-R3..R5: 404 for an unknown card or another user's card. */
    @PostMapping("/{cardId}")
    GradeResult grade(@PathVariable long cardId, @Valid @RequestBody GradeRequest request) {
        return service.grade(cardId, request.grade());
    }
}
