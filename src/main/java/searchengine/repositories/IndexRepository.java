package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.WebPage;
import searchengine.model.WebSite;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {
    @Transactional
    void deleteByPage(WebPage page);

    List<Index> findByPage(WebPage webPage);

    @Query("SELECT i FROM Index i WHERE i.lemma = :lemma")
    List<Index> findAllByLemma(@Param("lemma") Lemma lemma);

    @Query("SELECT i FROM Index i WHERE i.lemma IN :lemmas")
    List<Index> findAllByLemmas(@Param("lemmas") List<Lemma> lemmas);
}