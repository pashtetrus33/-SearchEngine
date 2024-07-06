package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.WebPage;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {
    Index findFirstByLemmaAndPage(Lemma lemma, WebPage webPage);
    List<Index> findAllByPage(WebPage webPage);
    @Transactional
    void deleteByPage(WebPage page);
}
