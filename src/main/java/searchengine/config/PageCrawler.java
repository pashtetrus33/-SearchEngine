package searchengine.config;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.model.WebPage;
import searchengine.model.WebSite;
import searchengine.repositories.WebPageRepository;
import searchengine.repositories.WebSiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class PageCrawler extends RecursiveAction {
    private static final int MIN_DELAY = 100; // Минимальная задержка в миллисекундах
    private static final int MAX_DELAY = 150; // Максимальная задержка в миллисекундах
    public static final CopyOnWriteArraySet<String> visitedLinks = new CopyOnWriteArraySet<>();

    private final String url;
    private final WebSiteRepository webSiteRepository;
    private final WebPageRepository webPageRepository;
    private final String userAgent;
    private final String referrer;
    private final WebSite webSite;


    public PageCrawler(WebSite webSite, WebSiteRepository webSiteRepository, WebPageRepository webPageRepository, String url, String userAgent, String referrer) {
        this.url = url;
        this.webSiteRepository = webSiteRepository;
        this.webPageRepository = webPageRepository;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.webSite = webSite;
    }

    @Override
    protected void compute() {
        try {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Task interrupted before execution: " + url);
                visitedLinks.clear();
                return;
            }
            if (visitedLinks.add(url)) {
                try {
                    Thread.sleep(MIN_DELAY + (int) (Math.random() * (MAX_DELAY - MIN_DELAY)));

                    Connection.Response response = Jsoup.connect(url)
                            .userAgent(userAgent)
                            .referrer(referrer)
                            .execute();

                    if (Thread.currentThread().isInterrupted()) {
                        log.info("Task interrupted after response: " + url);
                        visitedLinks.clear();
                        return;
                    }

                    Document document = response.parse();
                    if (isLink(url) && !isFile(url)) {
                        log.info("\nParsing page: " + url);
                        WebPage webPage = new WebPage();
                        webPage.setCode(response.statusCode());
                        webPage.setPath(url);
                        webPage.setContent(document.html());
                        webPage.setWebSite(webSite);
                        webSite.setStatusTime(LocalDateTime.now());
                        webSiteRepository.save(webSite);
                        webPageRepository.save(webPage);
                    }

                    List<PageCrawler> taskList = new ArrayList<>();
                    for (Element element : document.select("a[href]")) {
                        String link = element.absUrl("href");
                        if (!link.isEmpty() && isLink(link)) {
                            if (Thread.currentThread().isInterrupted()) {
                                log.info("Task interrupted before creating sub-task: " + link);
                                visitedLinks.clear();
                                return;
                            }

                            PageCrawler task = new PageCrawler(webSite, webSiteRepository, webPageRepository, link, userAgent, referrer);
                            task.fork(); // Отправляем задачу (запускаем асинхронно относительно текущего потока)
                            taskList.add(task);
                        }
                    }
                    for (PageCrawler task : taskList) {
                        task.join();
                    }

                } catch (InterruptedException e) {
                    log.warn("Thread interrupted: " + url, e);
                    visitedLinks.clear();
                    Thread.currentThread().interrupt(); // Важно восстанавливать статус прерывания
                } catch (SocketTimeoutException e) {
                    log.warn("Socket timeout: " + url, e);
                } catch (IOException e) {
                    log.warn("IO Exception: " + url, e);
                }
            } else {
                log.info("\nLink is already visited: " + url);
            }
        } finally {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Task interrupted at the end: " + url);
                visitedLinks.clear();
            }
        }
    }

    private boolean isLink(String link) {
        String cleanedUrl = cleanBaseUrl();
        return link.contains(cleanedUrl) && !link.contains("#");
    }

    private boolean isFile(String link) {
        link = link.toLowerCase();
        return link.endsWith(".jpg") || link.endsWith(".jpeg") || link.endsWith(".png") ||
                link.endsWith(".gif") || link.endsWith(".webp") || link.endsWith(".pdf") ||
                link.endsWith(".eps") || link.endsWith(".xlsx") || link.endsWith(".doc") ||
                link.endsWith(".pptx") || link.endsWith(".docx") || link.contains("?_ga");
    }

    private String cleanBaseUrl() {
        if (url.startsWith("http://www.")) {
            return url.substring(11);
        } else if (url.startsWith("https://www.")) {
            return url.substring(12);
        } else if (url.startsWith("http://")) {
            return url.substring(7);
        } else if (url.startsWith("https://")) {
            return url.substring(8);
        } else if (url.startsWith("www.")) {
            return url.substring(4);
        }
        return url;
    }
}