package searchengine.services;


import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IndexingService {
    void startIndexing();

    void stopIndexing();

    boolean indexingInProgress();

    SseEmitter getStatusEmitter();
}
