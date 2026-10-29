package br.com.aura.store;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class StoreController {
    private final StoreService store;
    public StoreController(StoreService store) { this.store = store; }

    @GetMapping("/products")
    public List<Product> products() { return store.products(); }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderReceipt checkout(@Valid @RequestBody CheckoutRequest request) {
        return store.checkout(request);
    }
}
