package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (indexingService.indexingInProgress()) {
            response.setResult(false);
            response.setError("Индексация уже запущена");
            return response;
        }

        try {
            indexingService.startIndexing();
            response.setResult(true);
            return response;
        } catch (Exception e) {
            response.setResult(false);
            response.setError("Ошибка при запуске индексации: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (!indexingService.indexingInProgress()) {
            response.setResult(false);
            response.setError("Индексация не запущена");
            return response;
        }

        try {
            indexingService.stopIndexing();
            response.setResult(true);
            return response;
        } catch (Exception e) {
            response.setResult(false);
            response.setError("Ошибка при остановке индексации: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/indexingStatus")
    public SseEmitter getIndexingStatus() {
        return indexingService.getStatusEmitter();
    }
}