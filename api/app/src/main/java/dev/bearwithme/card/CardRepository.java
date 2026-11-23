package dev.bearwithme.card;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface CardRepository extends JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    /** {@link CardContentLookup}: at most one review session (50 cards, FR-R1). */
    List<Card> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    /**
     * FR-C2; {@code lower(term)} matches the unique index {@code uq_cards_user_term}. Pass {@code excludeId = 0}
     * when creating (ids start at 1), the card's own id when updating.
     */
    @Query("select case when count(c) > 0 then true else false end from Card c"
            + " where c.userId = :userId and lower(c.term) = lower(:term) and c.id <> :excludeId")
    boolean existsTerm(Long userId, String term, Long excludeId);

    /** CSV import: all of the user's terms in one query (at most ~10,000, spec: Data size). */
    @Query("select lower(c.term) from Card c where c.userId = :userId")
    List<String> findLowerCaseTerms(Long userId);
}
