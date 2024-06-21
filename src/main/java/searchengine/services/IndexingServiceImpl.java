package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import searchengine.config.Site;
import searchengine.config.PageCrawler;
import searchengine.config.SitesConfig;
import searchengine.model.Status;
import searchengine.model.WebSite;
import searchengine.repositories.WebPageRepository;
import searchengine.repositories.WebSiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private static final Logger LOGGER = Logger.getLogger(IndexingServiceImpl.class.getName());

    private final SitesConfig sitesConfig;
    private final WebSiteRepository webSiteRepository;
    private final WebPageRepository webPageRepository;
    private final List<ForkJoinPool> forkJoinPoolList = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();


    @Override
    public void startIndexing() {

        for (Site site : sitesConfig.getSites()) {
            Thread thread = new Thread(() -> {
                try {
                    processSite(site, sitesConfig.getReferrer(), sitesConfig.getUserAgent());
                } catch (InterruptedException e) {
                    System.out.println("Thread was interrupted: " + Thread.currentThread().getName());
                    // Выход из метода при прерывании
                }
            });
            threads.add(thread);
            thread.start();
        }
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
        // Прерывание потоков
        threads.forEach(Thread::interrupt);
        threads.clear();

        // Завершение работы ForkJoinPool
        forkJoinPoolList.forEach(pool -> {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("ForkJoinPool did not terminate");
                }
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for ForkJoinPool to terminate");
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
    }
}
