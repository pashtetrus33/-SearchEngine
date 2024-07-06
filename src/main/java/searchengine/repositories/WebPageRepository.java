package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.WebPage;
import searchengine.model.WebSite;

import java.util.List;


@Repository
public interface WebPageRepository extends JpaRepository<WebPage, Integer> {

    @Transactional
    void deleteByWebSiteUrl(String siteUrl);

    @Transactional
    boolean existsWebPageByPath(String siteUrl);

    @Transactional
    void deleteWebPageByPath(String url);

    @Transactional
    WebPage findByPath(String url);

    // Используем аннотацию readOnly для методов, которые только читают данные
    @Transactional(readOnly = true)
    Long getCountByWebSite(WebSite site);

    @Transactional(readOnly = true)
    int countByWebSite(WebSite webSite);

    List<WebPage> findAllByWebSite(WebSite webSite);
}