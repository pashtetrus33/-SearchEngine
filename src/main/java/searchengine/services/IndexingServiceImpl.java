package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import searchengine.config.Site;
import searchengine.config.PageCrawler;
import searchengine.config.SitesConfig;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.WebPageRepository;
import searchengine.repositories.WebSiteRepository;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static searchengine.config.PageCrawler.visitedLinks;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private volatile boolean indexingInProgress = false; // Использование volatile для обеспечения видимости между потоками
    private final SitesConfig sitesConfig;
    private final WebSiteRepository webSiteRepository;
    private final WebPageRepository webPageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final List<ForkJoinPool> forkJoinPoolList = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final LemmaService lemmaService;

    @Override
    public void startIndexing() {

        if (indexingInProgress) {
            log.warn("Indexing is already in progress.");
            return;
        }

        indexingInProgress = true;
        ExecutorService executorService = Executors.newFixedThreadPool(sitesConfig.getSites().size());
        List<Future<?>> futures = new CopyOnWriteArrayList<>();

        for (Site site : sitesConfig.getSites()) {
            Future<?> future = executorService.submit(() -> {
                try {
                    if (Thread.interrupted()) {
                        throw new InterruptedException(); // Проверка прерывания потока
                    }
                    processSite(site, sitesConfig.getReferrer(), sitesConfig.getUserAgent());


                } catch (InterruptedException e) {
                    log.warn("Task was interrupted: " + Thread.currentThread().getName());
                    notifyClients("Indexing interrupted");
                    Thread.currentThread().interrupt(); // Установка флага прерывания потока
                }
            });
            futures.add(future);
        }

        // Запланировать завершение пула потоков после выполнения всех задач
        executorService.shutdown();

        new Thread(() -> {
            try {
                for (Future<?> future : futures) {
                    future.get();
                    // Блокировка до завершения всех задач
                }
                visitedLinks.clear();
                log.info("All tasks completed.");
                notifyClients("Indexing completed");

            } catch (Exception e) {
                log.warn("Exception occurred while waiting for task completion: " + e.getMessage());
            } finally {
                indexingInProgress = false;
            }
        }).start();
    }

    private void processSite(Site site, String referrer, String userAgent) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        deleteExistingData(site.getUrl());

        WebSite webSite = new WebSite();
        webSite.setUrl(site.getUrl());
        webSite.setName(site.getName());
        webSite.setStatus(Status.INDEXING);
        webSite.setStatusTime(LocalDateTime.now());
        webSiteRepository.save(webSite);

        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPoolList.add(forkJoinPool);
        forkJoinPool.invoke(new PageCrawler(webSite, webSiteRepository, webPageRepository, site.getUrl(), userAgent, referrer, lemmaService));

        webSite.setStatus(Status.INDEXED);
        webSite.setStatusTime(LocalDateTime.now());
        webSiteRepository.save(webSite);
    }

    public void deleteWebPage(Integer webPageId) {
        WebPage webPage = webPageRepository.findById(webPageId)
                .orElseThrow(() -> new RuntimeException("WebPage not found with id " + webPageId));

        // Удаление зависимых записей Index
        indexRepository.deleteByPage(webPage);
        // Удаление WebPage
        webPageRepository.delete(webPage);
    }

    private void deleteExistingData(String siteUrl) {
        WebSite webSite = webSiteRepository.findByUrl(siteUrl);
        webPageRepository.findAllByWebSite(webSite).forEach(p -> deleteWebPage(p.getId()));
        List<Lemma> allByWebSite = lemmaRepository.findAllByWebSite(webSite);
        lemmaRepository.deleteAll(allByWebSite);

        webSiteRepository.deleteByUrl(siteUrl);
    }

    @Override
    public void stopIndexing() {
        if (!indexingInProgress) {
            log.warn("No indexing in progress to stop.");
            return;
        }

        indexingInProgress = false;
        forkJoinPoolList.forEach(pool -> {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("ForkJoinPool did not terminate");
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for ForkJoinPool to terminate");
            }
        });
        forkJoinPoolList.clear();

        for (WebSite webSite : webSiteRepository.findByStatus(Status.INDEXING)) {
            webSite.setStatus(Status.FAILED);
            webSite.setStatusTime(LocalDateTime.now());
            webSite.setLastError("Индексация остановлена пользователем");
            webSiteRepository.save(webSite);
        }

        notifyClients("Indexing interrupted by user");
    }

    public SseEmitter getStatusEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Устанавливаем максимальный тайм-аут
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            try {
                emitter.send(SseEmitter.event().name("timeout").data("Connection timeout"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private void notifyClients(String message) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("indexingStatus").data(message));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    @Override
    public boolean indexingInProgress() {
        return indexingInProgress;
    }

    @Override
    public WebPage indexPage(String url) {
        Site site = isPageFromSite(url);
        if (site != null && !isInValidStatusCode(url)) {
            log.info("Site {}", site.getName());
            WebSite webSite = webSiteRepository.findByUrl(site.getUrl());
            WebPage webPage;
            if (webSite == null) {
                webSite = new WebSite();
                webSite.setUrl(site.getUrl());
                webSite.setName(site.getName());
                webSite.setStatus(Status.INDEXING);
                webSite.setStatusTime(LocalDateTime.now());
                webSiteRepository.save(webSite);
            } else if (webPageRepository.existsWebPageByPath(url)) {
                log.info("Page is already indexed. Reindexing...");
                webPage = webPageRepository.findByPath(url);
                deleteWebPage(webPage.getId());
            } else if (webPageRepository.existsWebPageByPath(addOrRemoveWww(url))) {
                log.info("Page is already indexed. Reindexing...");
                url = addOrRemoveWww(url);
                webPage = webPageRepository.findByPath(url);
                deleteWebPage(webPage.getId());
            }
            webPage = parsePage(url, webSite);
            return webPage;
        }
        return null;
    }

    private String addOrRemoveWww(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        String wwwPrefix = "www.";
        String protocolSeparator = "://";
        int protocolIndex = url.indexOf(protocolSeparator);
        String protocol = "";

        // Извлечение протокола (http, https и т.д.)
        if (protocolIndex != -1) {
            protocol = url.substring(0, protocolIndex + protocolSeparator.length());
            url = url.substring(protocolIndex + protocolSeparator.length());
        }

        if (url.startsWith(wwwPrefix)) {
            // Удаляем "www." если оно присутствует
            return protocol + url.substring(wwwPrefix.length());
        } else {
            // Добавляем "www." если его нет
            return protocol + wwwPrefix + url;
        }
    }

    private boolean isInValidStatusCode(String url) {
        int statusCode = 0;
        try {
            statusCode = Jsoup.connect(url).execute().statusCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Если код ответа 4xx или 5xx, не индексируем страницу
        return statusCode >= 400 && statusCode < 600;
    }

    @Override
    public String getContentByUrl(String url) {
        WebPage webPage = webPageRepository.findByPath(url);
        return webPage != null ? webPage.getContent() : null;
    }

    @Override
    public WebSite getWebSiteByUrl(String url) {
        WebSite webSite = webSiteRepository.findByUrl(url);
        if (webSite == null) {
            url = addWWWIfAbsent(url);
            webSite = webSiteRepository.findByUrl(url);
        }
        return webSite;
    }

    public String addWWWIfAbsent(String url) {
        if (url.matches("^(http://|https://)www\\..*")) {
            return url;
        }
        String regex = "^(http://|https://)";
        return url.replaceAll(regex, "$1www.");
    }

    private WebPage parsePage(String url, WebSite webSite) {
        Connection.Response response;
        Document document;
        try {
            response = Jsoup.connect(url).execute();
            document = response.parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Parsing page: " + url);
        WebPage webPage = new WebPage();
        webPage.setCode(response.statusCode());
        webPage.setPath(url);
        webPage.setContent(document.html());
        webPage.setWebSite(webSite);
        webSite.setStatus(Status.INDEXED);
        webSite.setStatusTime(LocalDateTime.now());
        webSiteRepository.save(webSite);
        webPageRepository.save(webPage);
        return webPage;
    }

    private Site isPageFromSite(String pageUrl) {
        try {
            URL pageUri = new URL(pageUrl);
            String pageHost = pageUri.getHost().replaceFirst("^www\\.", "");

            for (Site site : sitesConfig.getSites()) {
                URL siteUrl = new URL(site.getUrl());
                String siteHost = siteUrl.getHost().replaceFirst("^www\\.", "");

                if (pageHost.equalsIgnoreCase(siteHost)) {
                    return site;
                }
            }
        } catch (MalformedURLException e) {
            log.error("MalformedURLException {}", e.getMessage());
        }
        return null;
    }

    @Override
    public Long getWebPagesCount() {
        return webPageRepository.count();
    }

    @Override
    public Long getWebSitesCount() {
        return webSiteRepository.count();
    }

    @Override
    public List<WebSite> getAllWebSites() {
        return webSiteRepository.findAll();
    }

    @Override
    public Long getCountByWebSite(WebSite site) {
        return webPageRepository.getCountByWebSite(site);
    }
}