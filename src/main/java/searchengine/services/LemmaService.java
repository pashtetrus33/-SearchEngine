package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.WebPage;
import searchengine.model.WebSite;

import java.util.List;

@Service
public interface LemmaService {

    void saveAllLemmas(WebPage webPage);

    List<Lemma> findAllByWebSite(WebSite webSite);
}
