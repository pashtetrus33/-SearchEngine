package searchengine.config;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class LemmaTest {
    private static String text = "Повторное появление леопарда в Осетии позволяет предположить,\n" +
            "что леопард постоянно обитает в некоторых районах Северного\n" +
            "Кавказа.";

    public static void main(String[] args) throws IOException {
        LemmaFinder instance = LemmaFinder.getInstance();
        Map<String, Integer> map = instance.collectLemmas(text);

        // Распечатываем содержимое HashMap
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            System.out.println(entry.getKey() + " - " + entry.getValue());
        }

        Set<String> set = instance.getLemmaSet(text);

        // Распечатываем содержимое HashSet
        for (String value : set) {
            System.out.println(value);
        }
    }
}
