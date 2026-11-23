package dev.bearwithme.card;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
class TagController {

    private final CardService service;

    TagController(CardService service) {
        this.service = service;
    }

    /** Tags in use, alphabetical, with card counts (FR-C3, FR-C4 tag filter). */
    @GetMapping
    List<TagCount> list() {
        return service.tagCounts();
    }
}
