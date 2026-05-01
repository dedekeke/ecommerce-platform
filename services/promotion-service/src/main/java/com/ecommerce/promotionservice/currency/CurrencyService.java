package com.ecommerce.promotionservice.currency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Multi-currency conversion service (§3.5).
 *
 * <p>USD is the canonical base; every rate is stored as
 * {@code rateToUsd = unitsOfCurrencyPerOneUsd}. To convert {@code amount}
 * from currency {@code A} to currency {@code B}:
 * <pre>
 *     amountInUsd = amount / rateToUsd(A)
 *     amountInB   = amountInUsd * rateToUsd(B)
 * </pre>
 * Composing those gives a single multiplier {@code rate = rateToUsd(B) /
 * rateToUsd(A)} which is what the response exposes.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {

    /** Scale used for the multiplier returned by {@link #convert}. */
    private static final int RATE_SCALE = 8;
    /** Scale for the final converted amount — matches typical money handling. */
    private static final int AMOUNT_SCALE = 4;

    private final CurrencyRateRepository currencyRateRepository;

    @Transactional(readOnly = true)
    public CurrencyDtos.RatesResponse listRates() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        currencyRateRepository.findAll().forEach(r -> rates.put(r.getCode(), r.getRateToUsd()));
        return new CurrencyDtos.RatesResponse("USD", rates);
    }

    @Transactional(readOnly = true)
    public CurrencyDtos.ConvertResponse convert(CurrencyDtos.ConvertRequest req) {
        Objects.requireNonNull(req, "request must not be null");
        BigDecimal amount = req.amount() == null ? BigDecimal.ZERO : req.amount();
        String from = normalize(req.from());
        String to = normalize(req.to());

        BigDecimal fromRate = lookup(from);
        BigDecimal toRate = lookup(to);
        BigDecimal multiplier = toRate.divide(fromRate, RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal converted = amount.multiply(multiplier).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        log.debug("Convert {} {} -> {} {} (rate={})", amount, from, converted, to, multiplier);
        return new CurrencyDtos.ConvertResponse(converted, multiplier);
    }

    private BigDecimal lookup(String code) {
        return currencyRateRepository.findById(code)
            .map(CurrencyRate::getRateToUsd)
            .orElseThrow(() -> new IllegalArgumentException("Unknown currency code: " + code));
    }

    private static String normalize(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Currency code must not be null");
        }
        return code.trim().toUpperCase();
    }
}
