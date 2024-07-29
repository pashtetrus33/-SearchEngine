package searchengine.services;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import searchengine.model.WebPage;
import searchengine.model.WebSite;

import java.util.List;


public interface IndexingService {
    void startIndexing();

    void stopIndexing();

    boolean indexingInProgress();

    SseEmitter getStatusEmitter();

    WebPage indexPage(String url);

    WebSite getWebSiteByUrl(String url);

    int getTotalPagesCount();

    List<WebSite> getAllWebSites();
}