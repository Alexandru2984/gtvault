package gtshop.service;

import gtshop.model.PointTransaction;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.PointTransactionRepository;
import gtshop.repository.UserRepository;
import gtshop.util.Ids;
import gtshop.web.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Singurul loc in care se modifica soldul de puncte.
 *
 * <p>Toate operatiile cer o tranzactie deschisa si un utilizator obtinut prin {@link #lock(String)}
 * (SELECT ... FOR UPDATE). Astfel cererile concurente pe acelasi cont sunt serializate si nu mai
 * pot aparea revendicari duble sau double-spend.</p>
 */
@Service
public class WalletService {
    private static final int MAX_BALANCE = 50_000_000;

    private final UserRepository userRepository;
    private final PointTransactionRepository transactionRepository;
    private final TimeService time;

    @PersistenceContext
    private EntityManager entityManager;

    public WalletService(UserRepository userRepository, PointTransactionRepository transactionRepository, TimeService time) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.time = time;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UserProfile lock(String userId) {
        UserProfile user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        // Daca entitatea era deja in contextul de persistenta (citita inainte de blocare), Hibernate
        // o returneaza cu starea veche; dupa ce detinem blocarea, reincarcam starea curenta din DB.
        entityManager.refresh(user);
        if (user.isBanned()) {
            throw ApiException.forbidden("Contul este suspendat.");
        }
        return user;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PointTransaction credit(UserProfile lockedUser, int amount, TxType type, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Creditul trebuie sa fie pozitiv");
        }
        long next = (long) lockedUser.getActivityPoints() + amount;
        if (next > MAX_BALANCE) {
            throw ApiException.badRequest("Soldul maxim permis a fost atins.");
        }
        lockedUser.setActivityPoints((int) next);
        if (type.countsAsXp()) {
            lockedUser.setTotalPointsEarned((int) Math.min(MAX_BALANCE, (long) lockedUser.getTotalPointsEarned() + amount));
        }
        userRepository.save(lockedUser);
        return record(lockedUser, type, amount, description);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PointTransaction debit(UserProfile lockedUser, int amount, TxType type, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Debitul trebuie sa fie pozitiv");
        }
        if (lockedUser.getActivityPoints() < amount) {
            int missing = amount - lockedUser.getActivityPoints();
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_POINTS",
                    "Puncte insuficiente! Îți mai lipsesc " + missing + " AP.");
        }
        lockedUser.setActivityPoints(lockedUser.getActivityPoints() - amount);
        userRepository.save(lockedUser);
        return record(lockedUser, type, -amount, description);
    }

    private PointTransaction record(UserProfile user, TxType type, int signedAmount, String description) {
        String desc = description == null ? type.name() : description;
        if (desc.length() > 255) {
            desc = desc.substring(0, 252) + "...";
        }
        PointTransaction tx = new PointTransaction(Ids.prefixed("tx-"), user, type.name(), signedAmount,
                user.getActivityPoints(), time.now(), desc);
        return transactionRepository.save(tx);
    }
}
