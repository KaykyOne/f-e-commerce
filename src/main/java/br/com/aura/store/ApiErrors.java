package br.com.aura.store;

import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Comparator;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("name", "Nome"), Map.entry("email", "E-mail"), Map.entry("password", "Senha"),
            Map.entry("currentPassword", "Senha atual"), Map.entry("newPassword", "Nova senha"),
            Map.entry("phone", "Telefone"), Map.entry("address", "Endereço"), Map.entry("city", "Cidade"),
            Map.entry("zipCode", "CEP"), Map.entry("items", "Sacola"), Map.entry("category", "Categoria"),
            Map.entry("description", "Descrição"), Map.entry("price", "Preço"), Map.entry("originalPrice", "Preço original"),
            Map.entry("image", "Imagem"), Map.entry("color", "Cor"), Map.entry("badge", "Selo"), Map.entry("stock", "Estoque"));

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> business(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason() == null ? "Não foi possível concluir a operação." : error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException error) {
        FieldError first = error.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparing(FieldError::getField)).orElse(null);
        String detail = first == null ? "Dados inválidos." : describe(first);
        return ResponseEntity.badRequest().body(Map.of("message", "Confira os dados informados. " + detail, "detail", detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> malformed() {
        return ResponseEntity.badRequest().body(Map.of("message", "Formato de dados inválido."));
    }

    /** Mensagem em português, independente do idioma do servidor. */
    private static String describe(FieldError field) {
        String root = field.getField().replaceAll("\\[.*", "");
        String label = LABELS.getOrDefault(root, root);
        String problem = switch (field.getCode() == null ? "" : field.getCode()) {
            case "NotBlank", "NotNull" -> "é obrigatório";
            case "Email" -> "é inválido";
            case "Size" -> "tem tamanho inválido";
            case "Pattern" -> field.getDefaultMessage();
            case "Positive", "DecimalMin", "Min", "PositiveOrZero" -> "está abaixo do mínimo permitido";
            case "Max", "DecimalMax" -> "está acima do máximo permitido";
            default -> "é inválido";
        };
        return label + " " + problem + ".";
    }
}
