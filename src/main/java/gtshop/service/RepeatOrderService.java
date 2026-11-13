package gtshop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gtshop.model.CartItem;
import gtshop.model.PurchaseRecord;
import gtshop.model.Reward;
import gtshop.model.ShopOrder;
import gtshop.model.UserProfile;
import gtshop.repository.CartItemRepository;
import gtshop.repository.ShopOrderRepository;
import gtshop.repository.UserRepository;
import gtshop.service.PricingService.CartLine;
import gtshop.service.PricingService.Quote;
import gtshop.web.ApiException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repetarea pregătește coșul; plata și rezervarea stocului rămân exclusiv în checkout. */
@Service
public class RepeatOrderService {
    private final ShopOrderRepository orders;
    private final CartItemRepository cart;
    private final UserRepository users;
    private final WalletService wallet;
    private final QuoteService quotes;
    private final ObjectMapper json;
    private final TimeService time;

    public RepeatOrderService(ShopOrderRepository orders, CartItemRepository cart, UserRepository users,
                              WalletService wallet, QuoteService quotes, ObjectMapper json, TimeService time) {
        this.orders = orders;
        this.cart = cart;
        this.users = users;
        this.wallet = wallet;
        this.quotes = quotes;
        this.json = json;
        this.time = time;
    }

    public record Source(String rewardId, String name, long quantity, int inCart, long afterAddition) {}
    public record Preview(String orderId, String version, List<Source> items, Quote quote, List<String> issues, boolean canAdd) {}
    public record Result(boolean success, String message, Quote quote) {}
    private record Draft(String name, long quantity) {}
    private record Fingerprint(String userId, String orderId, List<CartLine> before, List<Source> items, Quote quote, List<String> issues) {}
    private record Prepared(Preview preview, List<CartLine> target, List<CartItem> existing, QuoteService.Priced priced) {}

    @Transactional(readOnly = true)
    public Preview preview(String userId, String orderId) {
        UserProfile user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        return prepare(user, orderId, false).preview();
    }

    @Transactional
    public Result add(String userId, String orderId, String version) {
        UserProfile user = wallet.lock(userId);
        Prepared plan = prepare(user, orderId, true);
        if (!plan.preview().version().equals(version)) {
            throw new ApiException(HttpStatus.CONFLICT, "REPEAT_CHANGED",
                    "Coșul, prețurile sau disponibilitatea s-au schimbat. Verifică noua previzualizare înainte de confirmare.");
        }
        if (!plan.preview().canAdd()) {
            throw new ApiException(HttpStatus.CONFLICT, "REPEAT_UNAVAILABLE", "Comanda nu poate fi repetată integral. Coșul a rămas neschimbat.");
        }
        Map<String, CartItem> existing = plan.existing().stream().collect(Collectors.toMap(CartItem::getRewardId, item -> item));
        Map<String, Integer> target = plan.target().stream().collect(Collectors.toMap(CartLine::rewardId, CartLine::quantity));
        for (Source source : plan.preview().items()) {
            Reward reward = plan.priced().rewards().get(source.rewardId());
            CartItem line = existing.get(source.rewardId());
            int quantity = target.get(source.rewardId());
            if (line == null) {
                cart.save(new CartItem(user, reward, quantity, time.now()));
            } else {
                line.setQuantity(quantity);
                line.setName(reward.getName());
                line.setPrice(reward.getPrice());
                cart.save(line);
            }
        }
        cart.flush();
        return new Result(true, "Produsele au fost adăugate în coș. Verifică totalul înainte de finalizarea comenzii.", plan.preview().quote());
    }

    private Prepared prepare(UserProfile user, String orderId, boolean lock) {
        if (orderId == null || orderId.isBlank() || orderId.length() > 32) throw ApiException.badRequest("Numărul comenzii nu este valid.");
        ShopOrder order = orders.findForUser(orderId, user.getId()).orElseThrow(() -> ApiException.notFound("Comanda nu a fost găsită."));
        List<CartItem> existing = cart.findForUser(user.getId());
        Map<String, Integer> before = new TreeMap<>();
        existing.forEach(item -> before.put(item.getRewardId(), item.getQuantity()));
        Map<String, Long> target = new TreeMap<>();
        before.forEach((id, quantity) -> target.put(id, quantity.longValue()));
        Map<String, Draft> source = new TreeMap<>();
        List<Source> items = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        if (order.getItems().isEmpty()) issues.add("Comanda nu conține produse care pot fi adăugate din nou.");
        for (PurchaseRecord item : order.getItems()) {
            String id = item.getRewardId();
            if (id == null || id.isBlank()) {
                items.add(new Source(null, item.getRewardName(), item.getQuantity(), 0, item.getQuantity()));
                issues.add("Un produs din comanda veche nu mai poate fi identificat în catalog.");
                continue;
            }
            source.merge(id, new Draft(item.getRewardName(), item.getQuantity()),
                    (first, next) -> new Draft(first.name(), Math.addExact(first.quantity(), next.quantity())));
        }
        for (var entry : source.entrySet()) {
            String id = entry.getKey();
            Draft item = entry.getValue();
            int current = before.getOrDefault(id, 0);
            long quantity = current + item.quantity();
            items.add(new Source(id, item.name(), item.quantity(), current, quantity));
            target.put(id, quantity);
            if (item.quantity() < 1 || quantity < 1 || quantity > 99) {
                issues.add(item.name() + ": cantitatea totală din coș trebuie să fie între 1 și 99.");
            }
        }
        if (target.size() > CartService.MAX_LINES) issues.add("Coșul poate conține cel mult " + CartService.MAX_LINES + " produse diferite. Ajustează coșul înainte de repetare.");
        if (target.entrySet().stream().anyMatch(entry -> !source.containsKey(entry.getKey()) && (entry.getValue() < 1 || entry.getValue() > 99))) {
            issues.add("Un produs deja în coș are o cantitate invalidă. Ajustează coșul înainte de repetare.");
        }
        // Nu trimitem cantități sau numere de linii peste limite către calculul pe int al prețurilor.
        List<CartLine> lines = issues.isEmpty() ? target.entrySet().stream().map(entry -> new CartLine(entry.getKey(), Math.toIntExact(entry.getValue()))).toList() : List.of();
        QuoteService.Priced priced = lines.isEmpty() ? null : quotes.quote(user, lines, null, lock);
        Quote quote = priced == null ? null : priced.quote();
        if (quote != null) issues.addAll(quote.issues());
        List<String> errors = issues.stream().distinct().toList();
        List<Source> sources = List.copyOf(items);
        List<CartLine> original = before.entrySet().stream().map(entry -> new CartLine(entry.getKey(), entry.getValue())).toList();
        String version = fingerprint(new Fingerprint(user.getId(), orderId, original, sources, quote, errors));
        Preview preview = new Preview(orderId, version, sources, quote, errors, errors.isEmpty() && quote != null && quote.valid() && !sources.isEmpty());
        return new Prepared(preview, lines, existing, priced);
    }

    private String fingerprint(Fingerprint value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Previzualizarea nu poate fi calculată", e);
        }
    }
}
