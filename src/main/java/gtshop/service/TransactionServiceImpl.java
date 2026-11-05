package gtshop.service;

import gtshop.model.PointTransaction;
import gtshop.model.UserProfile;
import gtshop.repository.PointTransactionRepository;
import gtshop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {

    private final PointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionServiceImpl(PointTransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PointTransaction recordTransaction(UserProfile user, String type, int amount, int balanceAfter, String description) {
        if (user == null) {
            return null;
        }

        if (amount > 0) {
            user.setTotalPointsEarned(user.getTotalPointsEarned() + amount);
            userRepository.save(user);
        }

        PointTransaction tx = new PointTransaction(
            "tx-" + UUID.randomUUID().toString().substring(0, 12),
            user,
            type,
            amount,
            balanceAfter,
            Instant.now(),
            description
        );
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PointTransaction> getRecentTransactions(UserProfile user) {
        if (user == null) {
            return List.of();
        }
        return transactionRepository.findTop25ByUserOrderByCreatedAtDesc(user);
    }
}
