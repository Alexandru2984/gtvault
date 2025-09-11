package gtshop.controller;

import gtshop.model.CartItem;
import gtshop.model.CartResponse;
import gtshop.service.CartService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;
    public CartController(CartService cartService) { this.cartService = cartService; }

    @PostMapping("/add")
    public Map<String,Object> addToCart(@RequestBody CartItem req) {
        cartService.addToCart(req);
        Map<String,Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Adăugat în coș.");
        resp.put("cart", buildCart());
        return resp;
    }

    @GetMapping
    public CartResponse viewCart() { return buildCart(); }

    private CartResponse buildCart() {
        List<CartItem> items = cartService.getCartItems();
        int totalPoints = items.stream().mapToInt(i -> i.getPrice() * i.getQuantity()).sum();
        int totalItems  = items.stream().mapToInt(CartItem::getQuantity).sum();
        CartResponse resp = new CartResponse();
        resp.setItems(items);
        resp.setTotalPoints(totalPoints);
        resp.setTotalItems(totalItems);
        return resp;
    }
}
