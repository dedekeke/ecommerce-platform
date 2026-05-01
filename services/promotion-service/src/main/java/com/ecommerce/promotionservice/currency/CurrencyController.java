package com.ecommerce.promotionservice.currency;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Currency-rate and conversion endpoints (§3.5).
 */
@Slf4j
@RestController
@RequestMapping("/api/currency")
@RequiredArgsConstructor
@Tag(name = "Currency", description = "Multi-currency rates and conversion (§3.5)")
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping("/rates")
    @Operation(summary = "List all configured currency rates (USD base)")
    public ResponseEntity<CurrencyDtos.RatesResponse> getRates() {
        return ResponseEntity.ok(currencyService.listRates());
    }

    @PostMapping("/convert")
    @Operation(summary = "Convert an amount from one ISO 4217 currency to another")
    public ResponseEntity<CurrencyDtos.ConvertResponse> convert(
        @Valid @RequestBody CurrencyDtos.ConvertRequest req
    ) {
        return ResponseEntity.ok(currencyService.convert(req));
    }
}
