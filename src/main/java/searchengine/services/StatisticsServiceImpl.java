package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesConfig;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;
import searchengine.model.WebSite;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.WebPageRepository;
import searchengine.repositories.WebSiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final WebSiteRepository webSiteRepository;
    private final WebPageRepository webPageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesConfig sitesConfig;

    @Override
    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics() {
        List<WebSite> sites = webSiteRepository.findAll();
        List<Site> configSites = sitesConfig.getSites();
        int totalConfigSites = configSites.size();
        int totalPages = (int) webPageRepository.count();
        Integer totalLemmas = lemmaRepository.countAllLemmas();
        int totalLemmasValue = totalLemmas != null ? totalLemmas : 0;

        boolean indexingInProgress = sites.stream().anyMatch(site -> site.getStatus() == Status.INDEXING);

        TotalStatistics total = new TotalStatistics();
        total.setSites(totalConfigSites);
        total.setPages(totalPages);
        total.setLemmas(totalLemmasValue);
        total.setIndexing(indexingInProgress);

        List<DetailedStatisticsItem> detailed = configSites.stream().map(configSite -> {
            Optional<WebSite> optionalSite = sites.stream().filter(site -> site.getUrl().equals(configSite.getUrl())).findFirst();
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(configSite.getName());
            item.setUrl(configSite.getUrl());
            if (optionalSite.isPresent()) {
                WebSite site = optionalSite.get();
                item.setPages(webPageRepository.countByWebSite(site));
                Integer siteLemmas = lemmaRepository.countLemmasByWebSite(site);
                item.setLemmas(siteLemmas != null ? siteLemmas : 0);
                item.setStatus(site.getStatus().name());
                if (site.getStatus().equals(Status.INDEXED)) {
                    item.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.ofHours(-4)));
                } else {
                    item.setStatusTime(LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(-4)));
                }

                item.setError("-");
                if (site.getLastError() != null && !site.getLastError().isEmpty()) {
                    item.setError(site.getLastError());
                }
            } else {
                item.setPages(0);
                item.setLemmas(0);
                item.setStatus("NOT INDEXED YET");
                item.setStatusTime(LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(-4)));
                item.setError("N/A");
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