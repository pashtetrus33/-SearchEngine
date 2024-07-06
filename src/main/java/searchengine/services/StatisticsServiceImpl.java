package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;
import searchengine.model.WebSite;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.WebPageRepository;
import searchengine.repositories.WebSiteRepository;
import searchengine.services.StatisticsService;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final WebSiteRepository webSiteRepository;
    private final WebPageRepository webPageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics() {
        List<WebSite> sites = webSiteRepository.findAll();
        int totalSites = sites.size();
        int totalPages = (int) webPageRepository.count();
        Integer totalLemmas = lemmaRepository.countAllLemmas();
        int totalLemmasValue = totalLemmas != null ? totalLemmas : 0; // Обработка null значения

        boolean indexingInProgress = sites.stream().anyMatch(site -> site.getStatus() == Status.INDEXING);

        TotalStatistics total = new TotalStatistics();
        total.setSites(totalSites);
        total.setPages(totalPages);
        total.setLemmas(totalLemmasValue);
        total.setIndexing(indexingInProgress);

        List<DetailedStatisticsItem> detailed = sites.stream().map(site -> {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setPages(webPageRepository.countByWebSite(site));
            Integer siteLemmas = lemmaRepository.countLemmasByWebSite(site);
            item.setLemmas(siteLemmas != null ? siteLemmas : 0); // Обработка null значения
            item.setStatus(site.getStatus().name());
            item.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.UTC));
            if (site.getLastError() != null && !site.getLastError().isEmpty()) {
                item.setError(site.getLastError());
            }
            return item;
        }).collect(Collectors.toList());

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);

        return response;
    }
}