package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
            if (indexingService.indexPage(url)) {
                response.put("result", true);
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
}