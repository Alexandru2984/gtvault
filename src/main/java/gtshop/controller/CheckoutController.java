package gtshop.controller;

import gtshop.model.CheckoutRequest;
import gtshop.model.CheckoutResponse;
import gtshop.service.CartService;
import gtshop.service.CheckoutService;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/checkout"})
@Tag(name="Checkout", description="Order checkout endpoint")
public class CheckoutController {
    private final CheckoutService checkoutService;
    private final UserService userService;

    public CheckoutController(CheckoutService checkoutService, UserService userService, CartService cartService) {
        this.checkoutService = checkoutService;
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary="Checkout cart items")
    public CheckoutResponse checkout(@RequestBody CheckoutRequest req) {
        CheckoutResponse res = this.checkoutService.checkout(req.getItems());
        if (!res.isSuccess()) {
            int total = req.getItems().stream().mapToInt(i -> i.getPrice() * i.getQuantity()).sum();
            int missing = Math.max(0, total - this.userService.getUser().getActivityPoints());
            res.setMissingPoints(Integer.valueOf(missing));
            res.setMessage("Nu de\u021bii suficiente AP! Mai ai nevoie de " + missing + " AP.");
        }
        return res;
    }
}

