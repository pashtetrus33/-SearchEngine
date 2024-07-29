package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.WebPage;
import searchengine.services.IndexingService;
import searchengine.services.LemmaService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final LemmaService lemmaService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<String> startIndexing() {
        JSONObject response = new JSONObject();
        if (indexingService.indexingInProgress()) {
            try {
                response.put("result", false);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            try {
                response.put("error", "Индексация уже запущена");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else {
            indexingService.startIndexing();
            try {
                response.put("result", true);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return new ResponseEntity<>(response.toString(), HttpStatus.OK);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<String> stopIndexing() {
        JSONObject response = new JSONObject();

        try {
            if (!indexingService.indexingInProgress()) {
                response.put("result", false);
                response.put("error", "Индексация не запущена");
            } else {
                indexingService.stopIndexing();
                response.put("result", true);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return new ResponseEntity<>(response.toString(), HttpStatus.OK);
    }

    @GetMapping("/indexingStatus")
    public SseEmitter getIndexingStatus() {
        return indexingService.getStatusEmitter();
    }

    @PostMapping("/indexPage")
    public ResponseEntity<String> indexPage(@RequestParam(name = "url", required = false) String url) {
        if (url == null) {
            return new ResponseEntity<>("", HttpStatus.BAD_REQUEST);
        }

        JSONObject response = new JSONObject();

        try {
            WebPage webPage = indexingService.indexPage(url);
            if (webPage != null) {
                response.put("result", true);
                lemmaService.saveAllLemmas(webPage);

            } else {
                response.put("result", false);
                response.put("error", "Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле или невалидна.");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }


        return new ResponseEntity<>(response.toString(), HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(value = "query") String query,
                                                 @RequestParam(value = "site", required = false) String site,
                                                 @RequestParam(value = "offset", defaultValue = "0") Integer offset,
                                                 @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        log.info("In ApiController search: query - {}", query);
        SearchResponse response = searchService.search(query.trim(), site, offset, limit);
        if (!response.isResult()) {
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(response);
    }
}