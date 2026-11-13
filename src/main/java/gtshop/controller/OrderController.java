package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.OrderService;
import gtshop.service.OrderHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Orders", description = "Comenzile mele și verificarea voucherelor")
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;
    private final CurrentUser currentUser;
    private final OrderHistoryService history;

    public OrderController(OrderService orders, CurrentUser currentUser, OrderHistoryService history) {
        this.orders = orders;
        this.currentUser = currentUser;
        this.history = history;
    }

    public record VoucherVerification(@NotBlank @Size(max = 40) String code) {}

    @GetMapping("/orders")
    @RateLimit(name = "order-history", limit = 60, windowSeconds = 60)
    @Operation(summary = "Rezumatele comenzilor mele, cu căutare, filtre și paginare")
    public OrderHistoryService.HistoryPage list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = "") String q,
                                               @RequestParam(defaultValue = "ALL") String status,
                                               @RequestParam(defaultValue = "ALL") String delivery,
                                               @RequestParam(defaultValue = "") String from,
                                               @RequestParam(defaultValue = "") String to,
                                               @RequestParam(defaultValue = "NEWEST") String sort) {
        return history.list(currentUser.requireId(), page, size, q, status, delivery, from, to, sort);
    }

    @GetMapping("/orders/last-shipping")
    @Operation(summary = "Ultima adresă de livrare folosită")
    public Map<String, String> lastShipping() {
        return orders.lastShipping(currentUser.requireId());
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Detaliile unei comenzi")
    public OrderService.OrderView get(@PathVariable String id) {
        return orders.get(currentUser.requireId(), id);
    }

    @PostMapping("/orders/{id}/cancel")
    @RateLimit(name = "order-cancel", limit = 10, windowSeconds = 60)
    @Operation(summary = "Anulează o comandă cu produse fizice încă neprocesată (rambursare integrală)")
    public OrderService.OrderView cancel(@PathVariable String id) {
        return orders.cancelByUser(currentUser.requireId(), id);
    }

    @GetMapping("/vouchers/verify")
    @RateLimit(name = "voucher-verify", limit = 20, windowSeconds = 60, scope = RateLimit.Scope.IP)
    @Operation(summary = "Verifică public un cod voucher (fără date despre proprietar)")
    public Map<String, Object> verify(@RequestParam String code) {
        return orders.verifyVoucher(code);
    }

    @PostMapping("/vouchers/verify")
    @RateLimit(name = "voucher-verify", limit = 20, windowSeconds = 60, scope = RateLimit.Scope.IP)
    @Operation(summary = "Verifică un voucher folosind codul din corpul cererii")
    public Map<String, Object> verifyBody(@Valid @RequestBody VoucherVerification request) {
        return orders.verifyVoucher(request.code());
    }
}
