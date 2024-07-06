package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Status;
import searchengine.model.WebSite;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface WebSiteRepository extends JpaRepository<WebSite, Integer> {
    WebSite findByUrl(String url);

    List<WebSite> findByStatus(Status status);

    @Transactional
    void deleteByUrl(String url);
}