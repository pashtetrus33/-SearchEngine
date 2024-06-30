package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import searchengine.config.Site;
import searchengine.config.PageCrawler;
import searchengine.config.SitesConfig;
import searchengine.model.Status;
import searchengine.model.WebPage;
import searchengine.model.WebSite;
import searchengine.repositories.WebPageRepository;
import searchengine.repositories.WebSiteRepository;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static searchengine.config.PageCrawler.visitedLinks;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private boolean indexingInProgress = false; // Флаг для отслеживания текущего состояния индексации
    private final SitesConfig sitesConfig;
    private final WebSiteRepository webSiteRepository;
    private final WebPageRepository webPageRepository;
    private final List<ForkJoinPool> forkJoinPoolList = new CopyOnWriteArrayList<>();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public void startIndexing() {
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

        // Если нужно отслеживать выполнение задач, можно использовать futures
        new Thread(() -> {
            boolean allTasksCompleted = true;
            for (Future<?> future : futures) {
                try {
                    future.get(); // Это будет блокировать текущий поток, но не основной
                } catch (Exception e) {
                    log.warn("Exception occurred while waiting for task completion: " + e.getMessage());
                    allTasksCompleted = false;
                }
            }
            if (allTasksCompleted) {
                log.info("All tasks completed.");
                visitedLinks.clear();
                notifyClients("Indexing completed");
            } else {
                log.warn("Indexing was interrupted.");
            }
            indexingInProgress = false;
        }).start();
    }

    private void processSite(Site site, String referrer, String userAgent) throws InterruptedException {
        // Пример проверки флага прерывания
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        // Удаление существующих данных
        deleteExistingData(site.getUrl());

        // Создание новой записи в таблице site со статусом INDEXING
        WebSite webSite = new WebSite();
        webSite.setUrl(site.getUrl());
        webSite.setName(site.getName());
        webSite.setStatus(Status.INDEXING);
        webSite.setStatusTime(LocalDateTime.now());
        webSiteRepository.save(webSite);

        // Начало обхода страниц сайта
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPoolList.add(forkJoinPool);
        forkJoinPool.invoke(new PageCrawler(webSite, webSiteRepository, webPageRepository, site.getUrl(), userAgent, referrer));

        // Изменение статуса на INDEXED после успешного обхода
        webSite.setStatus(Status.INDEXED);
        webSiteRepository.save(webSite);
    }

    private void deleteExistingData(String siteUrl) {
        // Удаление данных из таблицы page по указанному siteUrl
        webPageRepository.deleteByWebSiteUrl(siteUrl);
        // Удаление данных из таблицы site по указанному siteUrl
        webSiteRepository.deleteByUrl(siteUrl);
    }

    @Override
    public void stopIndexing() {
        indexingInProgress = false;
        // Прерывание потоков
        threads.forEach(Thread::interrupt);
        threads.clear();

        // Завершение работы ForkJoinPool
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

        // Обновление статуса сайтов
        for (WebSite webSite : webSiteRepository.findByStatus(Status.INDEXING)) {
            webSite.setStatus(Status.FAILED);
            webSite.setStatusTime(LocalDateTime.now());
            webSite.setLastError("Индексация остановлена пользователем");
            webSiteRepository.save(webSite);
        }

        // Уведомление клиентов о прерывании индексации
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
    public boolean indexPage(String url) {
        Site site = isPageFromSite(url);
        if (site != null) {
            log.info("Site {}", site);
            WebSite webSite = webSiteRepository.findByUrl(site.getUrl());
            if (webSite == null) {
                // Создание новой записи в таблице site со статусом INDEXING
                webSite = new WebSite();
                webSite.setUrl(site.getUrl());
                webSite.setName(site.getName());
                webSite.setStatus(Status.INDEXING);
                webSite.setStatusTime(LocalDateTime.now());
                webSiteRepository.save(webSite);
            } else {
                if (webPageRepository.existsWebPageByPath(url)) {
                    System.out.println("Page is already indexed");
                    //очищаем все об этой странице из таблиц page lemma index
                    webPageRepository.deleteWebPageByPath(url);
                }
            }
            parsePage(url, webSite);
            return true;
        }
        return false;
    }

    private void parsePage(String url, WebSite webSite) {
        Connection.Response response;
        Document document;
        try {
            response = Jsoup.connect(url).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            document = response.parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("\nParsing page: " + url);
        WebPage webPage = new WebPage();
        webPage.setCode(response.statusCode());
        webPage.setPath(url);
        webPage.setContent(document.html());
        webPage.setWebSite(webSite);
        webSite.setStatus(Status.INDEXED);
        webSite.setStatusTime(LocalDateTime.now());
        webSiteRepository.save(webSite);
        webPageRepository.save(webPage);
    }

    private Site isPageFromSite(String pageUrl) {
        try {
            URL pageUri = new URL(pageUrl);
            String pageHost = pageUri.getHost().replaceFirst("^www\\.", "");

            for (Site site : sitesConfig.getSites()) {
                URL siteUrl = new URL(site.getUrl());
                String siteHost = siteUrl.getHost().replaceFirst("^www\\.", "");

                // Сравниваем домены
                if (pageHost.equalsIgnoreCase(siteHost)) {
                    return site;
                }
            }
        } catch (MalformedURLException e) {
            log.error("MalformedURLException {}", e.getMessage());
            return null;
        }

        return null;
    }
}