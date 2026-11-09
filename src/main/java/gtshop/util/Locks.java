package gtshop.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Dupa un SELECT ... FOR UPDATE, Hibernate returneaza instanta deja existenta in contextul de persistenta
 * (daca entitatea fusese citita mai devreme in aceeasi tranzactie) fara sa-i actualizeze starea. Reincarcarea
 * dupa obtinerea blocarii garanteaza ca lucram pe valorile curente (stoc, utilizari, unitati vandute).
 */
@Component
public class Locks {
    @PersistenceContext
    private EntityManager entityManager;

    public <T> T refresh(T lockedEntity) {
        entityManager.refresh(lockedEntity);
        return lockedEntity;
    }
}
