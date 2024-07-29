package searchengine.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Slf4j
public class LemmaFinder {
    private final LuceneMorphology russianMorphology;
    private final LuceneMorphology englishMorphology;
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private static final Pattern RUSSIAN_PATTERN = Pattern.compile("[а-яА-Я]");
    private static final Pattern ENGLISH_PATTERN = Pattern.compile("[a-zA-Z]");

    private final ConcurrentMap<String, Set<String>> lemmaFormsMap = new ConcurrentHashMap<>();

    public static LemmaFinder getInstance() throws IOException {
        LuceneMorphology russianMorphology = new RussianLuceneMorphology();
        LuceneMorphology englishMorphology = new EnglishLuceneMorphology();
        return new LemmaFinder(russianMorphology, englishMorphology);
    }

    private LemmaFinder(LuceneMorphology russianMorphology, LuceneMorphology englishMorphology) {
        this.russianMorphology = russianMorphology;
        this.englishMorphology = englishMorphology;
    }

    private LemmaFinder() {
        throw new RuntimeException("Disallow construct");
    }

    /**
     * Метод разделяет текст на слова, находит все леммы и считает их количество.
     *
     * @param text текст из которого будут выбираться леммы
     * @return ключ является леммой, а значение количеством найденных лемм
     */
    public Map<String, Integer> collectLemmas(String text) {
        String[] words = arrayContainsWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank() || word.length() <= 2) {
                continue;
            }

            if (isMixedLanguageWord(word)) {
                log.warn("Mixed language word skipped: {}", word);
                continue;
            }

            if (RUSSIAN_PATTERN.matcher(word).find()) {
                processWord(word, russianMorphology, lemmas);
            } else if (ENGLISH_PATTERN.matcher(word).find()) {
                processWord(word, englishMorphology, lemmas);
            }
        }

        return lemmas;
    }

    private void processWord(String word, LuceneMorphology morphology, HashMap<String, Integer> lemmas) {
        List<String> wordBaseForms;
        try {
            wordBaseForms = morphology.getMorphInfo(word);
        } catch (Exception e) {
            log.warn("Failed to get morph info for word: {}", word, e);
            return;
        }

        if (anyWordBaseBelongToParticle(wordBaseForms)) {
            return;
        }

        List<String> normalForms = morphology.getNormalForms(word);
        if (normalForms.isEmpty()) {
            return;
        }

        String normalWord = normalForms.get(0);

        // Пропуск лемм длиной 1 или 2 символа
        if (normalWord.length() <= 2) {
            return;
        }

        lemmas.put(normalWord, lemmas.getOrDefault(normalWord, 0) + 1);
        lemmaFormsMap.computeIfAbsent(normalWord, k -> ConcurrentHashMap.newKeySet()).add(word);
    }

    /**
     * @param text текст из которого собираем все леммы
     * @return набор уникальных лемм найденных в тексте
     */
    public Set<String> getLemmaSet(String text) {
        String[] textArray = arrayContainsWords(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (word.isBlank() || word.length() <= 2 || isMixedLanguageWord(word)) {
                continue;
            }

            if (RUSSIAN_PATTERN.matcher(word).find()) {
                addLemmas(word, russianMorphology, lemmaSet);
            } else if (ENGLISH_PATTERN.matcher(word).find()) {
                addLemmas(word, englishMorphology, lemmaSet);
            }
        }
        return lemmaSet;
    }

    private void addLemmas(String word, LuceneMorphology morphology, Set<String> lemmaSet) {
        List<String> wordBaseForms;
        try {
            wordBaseForms = morphology.getMorphInfo(word);
        } catch (Exception e) {
            log.warn("Failed to get morph info for word: {}", word, e);
            return;
        }

        if (!anyWordBaseBelongToParticle(wordBaseForms)) {
            List<String> normalForms = morphology.getNormalForms(word);
            for (String normalForm : normalForms) {
                if (normalForm.length() > 2) {
                    lemmaSet.add(normalForm);
                }
            }
        }
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMixedLanguageWord(String word) {
        return RUSSIAN_PATTERN.matcher(word).find() && ENGLISH_PATTERN.matcher(word).find();
    }

    private String[] arrayContainsWords(String text) {
        text = stripHtmlTags(text);
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-яА-Яa-zA-Z\\s])", " ")
                .trim()
                .split("\\s+");
    }

    // Метод для удаления HTML тегов из строки
    public String stripHtmlTags(String html) {
        Document doc = Jsoup.parse(html);
        return Jsoup.clean(doc.body().html(), Safelist.none());
    }

    public ConcurrentMap<String, Set<String>> getLemmaFormsMap() {
        return lemmaFormsMap;
    }
}
