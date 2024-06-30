package searchengine.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

@Component
public class IndexCreationService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void createIndexIfNotExists() {
        Session session = entityManager.unwrap(Session.class);
        Transaction tx = session.beginTransaction();

        try {
            Query query = session.createSQLQuery("CREATE INDEX IF NOT EXISTS idx_path ON page (path(255))");
            query.executeUpdate();
            tx.commit();
        } catch (RuntimeException e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            session.close();
        }
    }
}

