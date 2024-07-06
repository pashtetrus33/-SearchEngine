package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.LemmaFinder;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.WebPage;
import searchengine.model.WebSite;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
    public void saveAllLemmas(WebPage page) {
        List<Lemma> lemmas;
        final int[] frequency = new int[1];
        Map<String, Integer> map = lemmaFinder.collectLemmas(page.getContent());
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            lemmas = lemmaRepository.findALLByLemma(entry.getKey());
            if (lemmas.size() > 0) {
                lemmas.forEach(l -> {
                    if (l != null && l.getWebSite().equals(page.getWebSite())) {
                        frequency[0] = l.getFrequency();
                        frequency[0]++;
                        l.setFrequency(frequency[0]);
                        lemmaRepository.save(l);

                    }
                });
            } else {
                frequency[0] = 1;
                lemmas.add(new Lemma());
                lemmas.get(0).setLemma(entry.getKey());
                lemmas.get(0).setFrequency(frequency[0]);
                lemmas.get(0).setWebSite(page.getWebSite());
                lemmaRepository.save(lemmas.get(0));
            }

            Index index = new Index();
            index.setLemma(lemmas.get(0));
            index.setPage(page);
            index.setRank(entry.getValue().floatValue());
            indexRepository.save(index);
        }
    }

    @Override
    public List<Lemma> findAllByWebSite(WebSite webSite) {
        return lemmaRepository.findAllByWebSite(webSite);
    }
}
