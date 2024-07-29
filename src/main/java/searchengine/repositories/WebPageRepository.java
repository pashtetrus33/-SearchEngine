package searchengine.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.WebPage;
import searchengine.model.WebSite;

import java.util.List;


@Repository
public interface WebPageRepository extends JpaRepository<WebPage, Integer> {

    @Transactional
    boolean existsWebPageByPath(String siteUrl);

    @Transactional
    WebPage findByPath(String url);

    // Используем аннотацию readOnly для методов, которые только читают данные
    @Transactional(readOnly = true)
    int countByWebSite(WebSite webSite);

    List<WebPage> findAllByWebSite(WebSite webSite);

    @Query("SELECT p FROM WebPage p WHERE p.content LIKE %:query% AND p.webSite = :site")
    List<WebPage> searchByQueryAndSite(String query, WebSite site, Pageable pageable);

    @Query("SELECT p FROM WebPage p WHERE p.content LIKE %:query%")
    List<WebPage> searchByQuery(String query, Pageable pageable);
}