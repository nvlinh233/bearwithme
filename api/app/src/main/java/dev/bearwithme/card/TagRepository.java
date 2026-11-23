package dev.bearwithme.card;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByUserIdAndNameIn(Long userId, Collection<String> names);

    /** Tags used by at least one card, with their card count. */
    @Query("select new dev.bearwithme.card.TagCount(t.name, count(c)) from Card c join c.tags t"
            + " where c.userId = :userId group by t.name order by t.name")
    List<TagCount> countCardsByTag(Long userId);
}
