package dev.bearwithme.card;

import dev.bearwithme.card.CardService.CardDetail;
import dev.bearwithme.card.CardService.CardInput;
import dev.bearwithme.card.CardService.CardSummary;
import dev.bearwithme.card.CardService.SortOrder;
import dev.bearwithme.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
class CardController {

    private final CardService service;

    CardController(CardService service) {
        this.service = service;
    }

    /** FR-C1, FR-C3: lengths, at most 10 lowercase kebab-case tags. */
    record CardRequest(
            @NotBlank @Size(max = 200) String term,
            @NotBlank @Size(max = 500) String meaning,
            @Size(max = 1000) String example,
            @Size(max = 1000) String note,
            @Size(max = 10) List<@NotBlank @Size(max = 50)
                    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase kebab-case")
                    String> tags) {

        CardInput toInput() {
            return new CardInput(term, meaning, example, note, tags);
        }
    }

    @GetMapping
    PageResponse<CardSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "created") @Pattern(regexp = "created|term") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(q, tag, SortOrder.valueOf(sort.toUpperCase()), page, size);
    }

    @GetMapping("/{id}")
    CardDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<CardDetail> create(@Valid @RequestBody CardRequest request) {
        CardDetail card = service.create(request.toInput());
        return ResponseEntity.created(URI.create("/api/cards/" + card.id())).body(card);
    }

    @PutMapping("/{id}")
    CardDetail update(@PathVariable long id, @Valid @RequestBody CardRequest request) {
        return service.update(id, request.toInput());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
