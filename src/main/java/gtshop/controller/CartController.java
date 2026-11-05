package gtshop.controller;

import gtshop.model.CartItem;
import gtshop.model.CartResponse;
import gtshop.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "Shopping cart endpoints")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    @Operation(summary = "Add item to cart")
    public Map<String, Object> addToCart(@RequestBody CartItem req) {
        this.cartService.addToCart(req);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Adăugat în coș.");
        resp.put("cart", this.buildCart());
        return resp;
    }

    @GetMapping
    @Operation(summary = "View cart contents")
    public CartResponse viewCart() {
        return this.buildCart();
    }

    private CartResponse buildCart() {
        List<CartItem> items = this.cartService.getCartItems();
        int totalPoints = items.stream().mapToInt(i -> i.getPrice() * i.getQuantity()).sum();
        int totalItems = items.stream().mapToInt(CartItem::getQuantity).sum();
        CartResponse resp = new CartResponse();
        resp.setItems(items);
        resp.setTotalPoints(totalPoints);
        resp.setTotalItems(totalItems);
        return resp;
    }
}
