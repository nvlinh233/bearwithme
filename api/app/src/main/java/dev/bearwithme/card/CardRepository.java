package dev.bearwithme.card;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface CardRepository extends JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    /**
     * FR-C2; {@code lower(term)} matches the unique index {@code uq_cards_user_term}. Pass {@code excludeId = 0}
     * when creating (ids start at 1), the card's own id when updating.
     */
    @Query("select case when count(c) > 0 then true else false end from Card c"
            + " where c.userId = :userId and lower(c.term) = lower(:term) and c.id <> :excludeId")
    boolean existsTerm(Long userId, String term, Long excludeId);
}
