package gtshop.controller.admin;

import gtshop.model.FlashDeal;
import gtshop.model.PromoCode;
import gtshop.model.Reward;
import gtshop.security.RateLimit;
import gtshop.service.admin.AdminAudit;
import gtshop.service.admin.AdminCatalogService;
import gtshop.service.admin.AdminCommunityService;
import gtshop.service.admin.AdminDashboardService;
import gtshop.service.admin.AdminOrderService;
import gtshop.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panoul de administrare. Accesul este restrictionat la rolul ADMIN in SecurityConfig (/api/admin/**);
 * rolul se citeste din baza de date la fiecare cerere, deci retragerea lui are efect imediat.
 * Toate modificarile sunt inregistrate in jurnalul de audit.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrare: comenzi, catalog, promoții, comunitate, utilizatori")
public class AdminController {
    private final AdminDashboardService dashboard;
    private final AdminOrderService orders;
    private final AdminCatalogService catalog;
    private final AdminCommunityService community;
    private final AdminUserService users;
    private final AdminAudit audit;

    public AdminController(AdminDashboardService dashboard, AdminOrderService orders, AdminCatalogService catalog,
                           AdminCommunityService community, AdminUserService users, AdminAudit audit) {
        this.dashboard = dashboard;
        this.orders = orders;
        this.catalog = catalog;
        this.community = community;
        this.users = users;
        this.audit = audit;
    }

    public record StatusRequest(String status, String trackingCode, String note) {}

    public record ReasonRequest(String reason) {}

    public record CodeRequest(String code) {}

    public record StockRequest(Integer delta, String reason) {}

    public record ModerationRequest(Boolean hidden, String reason) {}

    public record BroadcastRequest(String title, String body, String link) {}

    public record PointsRequest(Integer amount, String reason) {}

    public record BanRequest(Boolean banned, String reason) {}

    public record ProposalStatusRequest(String status, String note) {}

    // ------------------------------------------------------------------ panou

    @GetMapping("/stats")
    @Operation(summary = "Indicatorii principali (utilizatori, comenzi, economie, catalog, comunitate, grafice)")
    public Map<String, Object> stats() {
        return dashboard.stats();
    }

    @GetMapping("/audit")
    @Operation(summary = "Jurnalul acțiunilor administrative")
    public Map<String, Object> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(required = false) String targetType,
                                     @RequestParam(required = false) String targetId) {
        return audit.list(page, size, targetType, targetId);
    }

    // ------------------------------------------------------------------ comenzi

    @GetMapping("/orders")
    @Operation(summary = "Comenzi: filtrare după status și căutare (ID, email, nume, telefon)")
    public Map<String, Object> orders(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return orders.list(status, q, page, size);
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Detaliile unei comenzi, cu datele clientului")
    public AdminOrderService.AdminOrderView order(@PathVariable String id) {
        return orders.get(id);
    }

    @PostMapping("/orders/{id}/status")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Avansează statusul (în pregătire → expediată cu AWB → livrată) sau anulează cu rambursare")
    public AdminOrderService.AdminOrderView orderStatus(@PathVariable String id, @RequestBody StatusRequest body) {
        return orders.updateStatus(id, body.status(), body.trackingCode(), body.note());
    }

    @PostMapping("/orders/{id}/cancel")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Anulează comanda cu rambursare integrală (motiv obligatoriu)")
    public AdminOrderService.AdminOrderView cancelOrder(@PathVariable String id, @RequestBody ReasonRequest body) {
        return orders.cancel(id, body.reason());
    }

    @PostMapping("/vouchers/redeem")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Marchează un voucher ca folosit (o singură dată)")
    public Map<String, Object> redeem(@RequestBody CodeRequest body) {
        return orders.redeemVoucher(body.code());
    }

    // ------------------------------------------------------------------ catalog

    @GetMapping("/rewards")
    @Operation(summary = "Toate produsele (inclusiv inactive), cu vânzări, wishlist și notă")
    public List<Map<String, Object>> rewards() {
        return catalog.rewards();
    }

    @GetMapping("/rewards/{id}")
    @Operation(summary = "Datele produsului și versiunea necesară editării")
    public AdminCatalogService.Editor reward(@PathVariable String id) {
        return catalog.editor(id);
    }

    @PostMapping("/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Adaugă un produs")
    public Reward createReward(@RequestBody AdminCatalogService.RewardForm body) {
        return catalog.createReward(body);
    }

    @PutMapping("/rewards/{id}")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Modifică un produs (câmpurile omise rămân neschimbate; dezactivarea îl ascunde din magazin)")
    public Reward updateReward(@PathVariable String id, @RequestBody AdminCatalogService.RewardForm body) {
        return catalog.updateReward(id, body);
    }

    @PostMapping("/rewards/{id}/stock")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Ajustează stocul (+ reaprovizionare / − corecție); anunță wishlist-urile la revenirea în stoc")
    public Reward stock(@PathVariable String id, @RequestBody StockRequest body) {
        return catalog.adjustStock(id, body.delta(), body.reason());
    }

    @GetMapping("/flash-deals")
    @Operation(summary = "Ofertele flash (active, programate și din ultimele 30 de zile)")
    public List<Map<String, Object>> deals() {
        return catalog.deals();
    }

    @PostMapping("/flash-deals")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Creează o ofertă flash (fără suprapuneri pe același produs)")
    public FlashDeal createDeal(@RequestBody AdminCatalogService.DealForm body) {
        return catalog.createDeal(body);
    }

    @PostMapping("/flash-deals/{id}/end")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Încheie acum o ofertă (cele programate sunt șterse)")
    public Map<String, Object> endDeal(@PathVariable String id) {
        return catalog.endDeal(id);
    }

    @GetMapping("/promo-codes")
    @Operation(summary = "Codurile promoționale, cu utilizări și reducerea totală acordată")
    public List<Map<String, Object>> promos() {
        return catalog.promos();
    }

    @PostMapping("/promo-codes")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Creează un cod promoțional")
    public PromoCode createPromo(@RequestBody AdminCatalogService.PromoForm body) {
        return catalog.createPromo(body);
    }

    @PutMapping("/promo-codes/{id}")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Modifică un cod promoțional (codul în sine nu poate fi redenumit)")
    public PromoCode updatePromo(@PathVariable String id, @RequestBody AdminCatalogService.PromoForm body) {
        return catalog.updatePromo(id, body);
    }

    // ------------------------------------------------------------------ comunitate

    @GetMapping("/proposals")
    @Operation(summary = "Propunerile comunității (cele în așteptare primele)")
    public List<Map<String, Object>> proposals(@RequestParam(required = false) String status) {
        return community.proposals(status);
    }

    @PostMapping("/proposals/{id}/status")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Aprobă / respinge / marchează ca implementată o propunere (autorul este notificat)")
    public Map<String, Object> proposalStatus(@PathVariable String id, @RequestBody ProposalStatusRequest body) {
        return community.setProposalStatus(id, body.status(), body.note());
    }

    @GetMapping("/reviews/reported")
    @Operation(summary = "Recenziile raportate, ordonate după numărul de rapoarte")
    public List<Map<String, Object>> reported(@RequestParam(defaultValue = "false") boolean includeHidden) {
        return community.reportedReviews(includeHidden);
    }

    @PostMapping("/reviews/{id}/moderate")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Ascunde o recenzie sau o aprobă (închizând rapoartele)")
    public Map<String, Object> moderate(@PathVariable String id, @RequestBody ModerationRequest body) {
        return community.moderateReview(id, body.hidden() != null && body.hidden(), body.reason());
    }

    @PostMapping("/notifications/broadcast")
    @RateLimit(name = "admin-broadcast", limit = 5, windowSeconds = 3600)
    @Operation(summary = "Trimite un anunț tuturor membrilor activi")
    public Map<String, Object> broadcast(@RequestBody BroadcastRequest body) {
        return community.broadcast(body.title(), body.body(), body.link());
    }

    // ------------------------------------------------------------------ utilizatori

    @GetMapping("/users")
    @Operation(summary = "Caută utilizatori (ID, email, nume, cod de recomandare)")
    public Map<String, Object> users(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size) {
        return users.search(q, page, size);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Detaliile unui cont: tranzacții, comenzi, istoric administrativ")
    public Map<String, Object> user(@PathVariable String id) {
        return users.detail(id);
    }

    @PostMapping("/users/{id}/points")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Corectează soldul (+/-) cu motiv; nu afectează XP-ul")
    public Map<String, Object> points(@PathVariable String id, @RequestBody PointsRequest body) {
        return users.adjustPoints(id, body.amount(), body.reason());
    }

    @PostMapping("/users/{id}/ban")
    @RateLimit(name = "admin-write", limit = 300, windowSeconds = 60)
    @Operation(summary = "Suspendă sau reactivează un cont (sesiunile existente sunt invalidate)")
    public Map<String, Object> ban(@PathVariable String id, @RequestBody BanRequest body) {
        return users.setBanned(id, body.banned(), body.reason());
    }
}
