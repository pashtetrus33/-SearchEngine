package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.LemmaFinder;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.WebPage;
import searchengine.model.WebSite;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaServiceImpl implements LemmaService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private LemmaFinder lemmaFinder;

    {
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    @Transactional
    public synchronized void saveAllLemmas(WebPage page) {
        Map<String, Integer> map = lemmaFinder.collectLemmas(page.getContent());
        WebSite site = page.getWebSite();

        map.forEach((lemma, count) -> {
            for (int i = 0; i < count; i++) {
                // Сохранение или обновление леммы в синхронизированном методе или блоке
                synchronized (this) {
                    lemmaRepository.saveOrUpdateLemma(lemma, site);
                }
            }

            // Создание индекса в синхронизированном блоке
            synchronized (this) {
                Lemma savedLemma = lemmaRepository.findByLemmaAndWebSite(lemma, site);
                Index index = new Index();
                index.setLemma(savedLemma);
                index.setPage(page);
                index.setRank(count.floatValue());
                indexRepository.save(index);
            }
        });
    }


    @Override
    public List<Index> findAllIndicesByWebPage(WebPage webPage) {
        return indexRepository.findByPage(webPage);
    }

    @Override
    public Map<String, Integer> collectLemmas(String text) {
        return lemmaFinder.collectLemmas(text);
    }

    @Override
    public ConcurrentMap<String, Set<String>> getLemmaForms() {
        return lemmaFinder.getLemmaFormsMap();
    }

    @Override
    public int getLemmaFrequency(String lemma) {
        return lemmaRepository.findALLByLemma(lemma).size();
    }

    @Override
    public Lemma findByLemmaAndWebSite(String s, WebSite webSite) {
        return lemmaRepository.findByLemmaAndWebSite(s, webSite);
    }

    @Override
    public List<Lemma> findALLByLemma(String s) {
        return lemmaRepository.findALLByLemma(s);
    }

    @Override
    public List<WebPage> findAllPagesByLemmaAndWebSite(String lemmaText, WebSite webSite) {
        Lemma lemmaByLemmaAndWebSite = lemmaRepository.findByLemmaAndWebSite(lemmaText, webSite);
        if (lemmaByLemmaAndWebSite == null) {
            log.warn("Lemma not found for lemmaText: {} and webSite: {}", lemmaText, webSite.getUrl());
            return Collections.emptyList();
        }

        List<Index> indices = indexRepository.findAllByLemma(lemmaByLemmaAndWebSite);
        if (indices.isEmpty()) {
            log.warn("No indices found for lemma: {} on webSite: {}", lemmaByLemmaAndWebSite.getLemma(), webSite.getUrl());
            return Collections.emptyList();
        }

        // Извлечение уникальных страниц из списка индексов
        List<WebPage> pages = indices.stream()
                .map(Index::getPage)
                .distinct()
                .collect(Collectors.toList());

        return pages;
    }


    @Override
    public List<WebPage> findAllPagesByLemmas(List<Lemma> lemmas) {
        List<Index> indices = indexRepository.findAllByLemmas(lemmas);
        return indices.stream()
                .map(Index::getPage)
                .distinct()
                .collect(Collectors.toList());
    }
}