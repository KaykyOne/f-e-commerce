package br.com.aura.store.admin;

import br.com.aura.store.admin.AdminDtos.AdminOrder;
import br.com.aura.store.admin.AdminDtos.AdminProduct;
import br.com.aura.store.admin.AdminDtos.ProductRequest;
import br.com.aura.store.admin.AdminDtos.Summary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Acesso restrito a ROLE_ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) { this.admin = admin; }

    @GetMapping("/summary")
    public Summary summary() { return admin.summary(); }

    @GetMapping("/products")
    public List<AdminProduct> products() { return admin.products(); }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProduct create(@Valid @RequestBody ProductRequest request) { return admin.create(request); }

    @PutMapping("/products/{id}")
    public AdminProduct update(@PathVariable long id, @Valid @RequestBody ProductRequest request) { return admin.update(id, request); }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { admin.deactivate(id); }

    @GetMapping("/orders")
    public List<AdminOrder> orders() { return admin.orders(); }
}
