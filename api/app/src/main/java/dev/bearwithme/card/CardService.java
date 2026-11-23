package dev.bearwithme.card;

import dev.bearwithme.auth.CurrentUser;
import dev.bearwithme.common.error.ConflictException;
import dev.bearwithme.common.error.NotFoundException;
import dev.bearwithme.common.web.PageResponse;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-C1..C6. Every method works on the current user's cards only; other users' cards are "not found". */
@Service
@Transactional
class CardService {

    private final CardRepository cards;
    private final TagRepository tags;
    private final CardScheduleLookup schedule;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CardService(CardRepository cards, TagRepository tags, CardScheduleLookup schedule, CurrentUser currentUser,
            ApplicationEventPublisher events, Clock clock) {
        this.cards = cards;
        this.tags = tags;
        this.schedule = schedule;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
    }

    record CardInput(String term, String meaning, String example, String note, List<String> tags) {
    }

    record CardDetail(long id, String term, String meaning, String example, String note, List<String> tags,
            Instant createdAt, Instant updatedAt, LocalDate dueDate) {
    }

    record CardSummary(long id, String term, String meaning, List<String> tags, LocalDate dueDate) {
    }

    enum SortOrder {
        CREATED(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        TERM(Sort.by(Sort.Order.asc("term"), Sort.Order.asc("id")));

        final Sort sort;

        SortOrder(Sort sort) {
            this.sort = sort;
        }
    }

    /** FR-C4: search term and meaning, filter by one tag, sort, page. */
    @Transactional(readOnly = true)
    PageResponse<CardSummary> list(String q, String tag, SortOrder sortOrder, int page, int size) {
        long userId = currentUser.id();
        Page<Card> result = cards.findAll(filter(userId, q, tag), PageRequest.of(page, size, sortOrder.sort));
        Map<Long, LocalDate> due = schedule.dueDates(result.map(Card::getId).getContent());
        return PageResponse.of(result,
                c -> new CardSummary(c.getId(), c.getTerm(), c.getMeaning(), tagNames(c), due.get(c.getId())));
    }

    @Transactional(readOnly = true)
    CardDetail get(long id) {
        return toDetail(find(id));
    }

    /** FR-C5: the review module creates the review state when it receives {@link CardCreatedEvent}. */
    CardDetail create(CardInput input) {
        long userId = currentUser.id();
        String term = normalize(input.term());
        requireUniqueTerm(userId, term, 0L);
        Card card = insert(userId, term, input, resolveTags(userId, input.tags(), existingTags(userId, input.tags())),
                clock.instant());
        return toDetail(card);
    }

    record ImportCounts(int created, int skipped) {
    }

    /**
     * FR-I3, FR-I4: inserts already validated rows in this one transaction. Terms that exist, or appear
     * earlier in the same file, are skipped. Terms and tags are loaded once instead of once per row.
     */
    ImportCounts importRows(List<CardInput> rows) {
        long userId = currentUser.id();
        Instant now = clock.instant();
        Set<String> knownTerms = new HashSet<>(cards.findLowerCaseTerms(userId));
        Map<String, Tag> tagCache = tags.findByUserId(userId).stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity()));
        int created = 0;
        int skipped = 0;
        for (CardInput row : rows) {
            String term = normalize(row.term());
            if (!knownTerms.add(term.toLowerCase(Locale.ROOT))) {
                skipped++;
                continue;
            }
            insert(userId, term, row, resolveTags(userId, row.tags(), tagCache), now);
            created++;
        }
        return new ImportCounts(created, skipped);
    }

    private Card insert(long userId, String term, CardInput input, Set<Tag> cardTags, Instant now) {
        Card card = new Card(userId, now);
        card.update(term, normalize(input.meaning()), normalize(input.example()), normalize(input.note()),
                cardTags, now);
        cards.save(card); // IDENTITY: the INSERT runs now, so the id is known
        events.publishEvent(new CardCreatedEvent(card.getId(), userId));
        return card;
    }

    CardDetail update(long id, CardInput input) {
        Card card = find(id);
        String term = normalize(input.term());
        requireUniqueTerm(card.getUserId(), term, card.getId());
        card.update(term, normalize(input.meaning()), normalize(input.example()), normalize(input.note()),
                resolveTags(card.getUserId(), input.tags(), existingTags(card.getUserId(), input.tags())),
                clock.instant());
        return toDetail(card); // dirty checking writes the UPDATE at commit
    }

    /** FR-C6: card_tags, review_states and review_logs go with it (ON DELETE CASCADE in V1). */
    void delete(long id) {
        cards.delete(find(id));
    }

    @Transactional(readOnly = true)
    List<TagCount> tagCounts() {
        return tags.countCardsByTag(currentUser.id());
    }

    private Card find(long id) {
        return cards.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Card " + id + " not found"));
    }

    private void requireUniqueTerm(long userId, String term, long excludeId) {
        if (cards.existsTerm(userId, term, excludeId)) {
            throw new ConflictException("A card with term '" + term + "' already exists");
        }
    }

    private Map<String, Tag> existingTags(long userId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        return tags.findByUserIdAndNameIn(userId, new LinkedHashSet<>(names)).stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity()));
    }

    /** FR-C3: existing tags are reused, unknown ones are created and added to {@code cache}. */
    private Set<Tag> resolveTags(long userId, List<String> names, Map<String, Tag> cache) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        Set<Tag> result = new HashSet<>();
        for (String name : new LinkedHashSet<>(names)) {
            result.add(cache.computeIfAbsent(name, n -> tags.save(new Tag(userId, n))));
        }
        return result;
    }

    private CardDetail toDetail(Card c) {
        LocalDate dueDate = schedule.dueDates(List.of(c.getId())).get(c.getId());
        return new CardDetail(c.getId(), c.getTerm(), c.getMeaning(), c.getExample(), c.getNote(), tagNames(c),
                c.getCreatedAt(), c.getUpdatedAt(), dueDate);
    }

    private static List<String> tagNames(Card card) {
        return card.getTags().stream().map(Tag::getName).sorted().toList();
    }

    /** Trims; blank optional fields are stored as NULL. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Specification<Card> filter(long userId, String q, String tag) {
        return (Root<Card> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                jakarta.persistence.criteria.CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.strip().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("term")), pattern, '\\'),
                        cb.like(cb.lower(root.get("meaning")), pattern, '\\')));
            }
            if (tag != null && !tag.isBlank()) {
                // EXISTS instead of a join: no duplicate rows, and the count query stays correct
                Subquery<Long> withTag = query.subquery(Long.class);
                Root<Card> sameCard = withTag.correlate(root);
                Join<Card, Tag> tags = sameCard.join("tags");
                withTag.select(cb.literal(1L)).where(cb.equal(tags.get("name"), tag.strip()));
                predicates.add(cb.exists(withTag));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** User input is literal text: escape LIKE wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
