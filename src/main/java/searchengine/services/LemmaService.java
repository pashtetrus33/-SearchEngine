package searchengine.services;

import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.WebPage;
import searchengine.model.WebSite;;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public interface LemmaService {
    void saveAllLemmas(WebPage webPage);

    List<Index> findAllIndicesByWebPage(WebPage webPage);

    Map<String, Integer> collectLemmas(String text);

    ConcurrentMap<String, Set<String>> getLemmaForms();

    int getLemmaFrequency(String lemma);

    List<WebPage> findAllPagesByLemmas(List<Lemma> lemmas);
    Lemma findByLemmaAndWebSite(String s, WebSite webSite);

    List<Lemma> findALLByLemma(String s);

    List<WebPage> findAllPagesByLemmaAndWebSite(String lemma, WebSite webSite);
}