package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.WebSite;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    List<Lemma> findALLByLemma(String s);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.webSite = :webSite")
    Lemma findByLemmaAndWebSite(@Param("lemma") String lemma, @Param("webSite") WebSite webSite);

    List<Lemma> findAllByWebSite(WebSite webSite);

    @Query("SELECT COUNT (l.lemma) FROM Lemma l WHERE l.webSite = :webSite")
    Integer countLemmasByWebSite(WebSite webSite);

    @Query("SELECT COUNT (l.lemma) FROM Lemma l")
    Integer countAllLemmas();

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO lemma (lemma, frequency, site_id) " +
            "VALUES (:lemma, 1, :webSite) " +
            "ON DUPLICATE KEY UPDATE frequency = frequency + 1",
            nativeQuery = true)
    void saveOrUpdateLemma(@Param("lemma") String lemma, @Param("webSite") WebSite webSite);
}