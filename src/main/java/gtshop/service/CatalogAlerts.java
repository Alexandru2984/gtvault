package gtshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fan-out-ul wishlist rulează după eliberarea blocărilor catalogului, numai pentru modificări confirmate. */
@Component
public class CatalogAlerts {
    private static final Logger log = LoggerFactory.getLogger(CatalogAlerts.class);
    private final ApplicationEventPublisher events;
    private final WishlistAlerts wishlist;
    private final TransactionTemplate transaction;

    public CatalogAlerts(ApplicationEventPublisher events, WishlistAlerts wishlist, PlatformTransactionManager manager) {
        this.events = events;
        this.wishlist = wishlist;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public record Alert(String rewardId, String name, Integer percent) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void backInStock(String id, String name) {
        events.publishEvent(new Alert(id, name, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void dealStarted(String id, String name, int percent) {
        events.publishEvent(new Alert(id, name, percent));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(Alert alert) {
        try {
            transaction.executeWithoutResult(status -> {
                if (alert.percent() == null) wishlist.backInStock(alert.rewardId(), alert.name());
                else wishlist.dealStarted(alert.rewardId(), alert.name(), alert.percent());
            });
        } catch (RuntimeException e) {
            // Catalogul este deja confirmat. Nu raportăm eșecul operației și nu logăm datele destinatarilor.
            log.warn("Notificarea wishlist pentru {} nu a putut fi livrată ({})", alert.rewardId(), e.getClass().getSimpleName());
        }
    }
}
