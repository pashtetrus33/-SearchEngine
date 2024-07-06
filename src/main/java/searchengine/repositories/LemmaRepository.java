package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.WebSite;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    List<Lemma> findALLByLemma(String s);

    List<Lemma> findAllByWebSite(WebSite webSite);

    @Query("SELECT COUNT (l.lemma) FROM Lemma l WHERE l.webSite = :webSite")
    Integer countLemmasByWebSite(WebSite webSite);

    @Query("SELECT COUNT (l.lemma) FROM Lemma l")
    Integer countAllLemmas();
}