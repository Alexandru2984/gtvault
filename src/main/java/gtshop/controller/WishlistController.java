package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist")
@Tag(name = "Wishlist", description = "Produsele dorite (cu alerte de reducere și stoc)")
public class WishlistController {
    private final WishlistService wishlist;
    private final CurrentUser currentUser;

    public WishlistController(WishlistService wishlist, CurrentUser currentUser) {
        this.wishlist = wishlist;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Wishlist-ul meu, cu prețuri și oferte la zi")
    public Map<String, Object> list() {
        return wishlist.list(currentUser.requireId());
    }

    @GetMapping("/ids")
    @Operation(summary = "Doar ID-urile produselor din wishlist (pentru inimioarele din catalog)")
    public List<String> ids() {
        return wishlist.ids(currentUser.requireId());
    }

    @PutMapping("/{rewardId}")
    @RateLimit(name = "wishlist", limit = 120, windowSeconds = 3600)
    @Operation(summary = "Adaugă un produs în wishlist (idempotent)")
    public Map<String, Object> add(@PathVariable String rewardId) {
        return wishlist.add(currentUser.requireId(), rewardId);
    }

    @DeleteMapping("/{rewardId}")
    @RateLimit(name = "wishlist", limit = 120, windowSeconds = 3600)
    @Operation(summary = "Scoate un produs din wishlist")
    public Map<String, Object> remove(@PathVariable String rewardId) {
        return wishlist.remove(currentUser.requireId(), rewardId);
    }
}
