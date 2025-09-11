package gtshop.controller;

import gtshop.model.CheckoutRequest;
import gtshop.model.CheckoutResponse;
import gtshop.service.CartService;
import gtshop.service.CheckoutService;
import gtshop.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final CheckoutService checkoutService;
    private final UserService userService;
    private final CartService cartService;

    public CheckoutController(CheckoutService checkoutService, UserService userService, CartService cartService) {
        this.checkoutService = checkoutService;
        this.userService = userService;
        this.cartService = cartService;
    }

    @PostMapping
    public CheckoutResponse checkout(@RequestBody CheckoutRequest req) {
        CheckoutResponse res = checkoutService.checkout(req.getItems());
        if (!res.isSuccess()) {
            int total = req.getItems().stream().mapToInt(i -> i.getPrice() * i.getQuantity()).sum();
            int missing = Math.max(0, total - userService.getUser().getActivityPoints());
            res.setMissingPoints(missing);
            res.setMessage("Nu deții suficiente AP! Mai ai nevoie de " + missing + " AP.");
        }
        return res;
    }
}
