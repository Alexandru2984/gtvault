package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Orders", description = "Comenzile mele și verificarea voucherelor")
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;
    private final CurrentUser currentUser;

    public OrderController(OrderService orders, CurrentUser currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @GetMapping("/orders")
    @Operation(summary = "Istoricul comenzilor (paginat)")
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return orders.list(currentUser.requireId(), page, size);
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
}
