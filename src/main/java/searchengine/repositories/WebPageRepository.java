package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.WebPage;
import searchengine.model.WebSite;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface WebPageRepository extends JpaRepository<WebPage, Integer> {

    @Transactional
    void deleteByWebSiteUrl(String siteUrl);

    @Transactional
    boolean existsWebPageByPath(String siteUrl);

    @Transactional
    void deleteWebPageByPath(String url);
}
