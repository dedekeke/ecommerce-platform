package com.ecommerce.searchservice.admin;

import com.ecommerce.common.featureflag.FeatureFlags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Guarded admin endpoint for the {@code brand}/{@code rating} index backfill
 * (§3.12 follow-up).
 *
 * <p>The whole endpoint is feature-flagged: it responds {@code 404 Not Found}
 * unless {@code FEATURE_FLAG_ES_BACKFILL=true} (or
 * {@code feature.flag.es-backfill=true}). This keeps a one-shot operational
 * tool from being reachable in normal operation; an operator flips the flag,
 * runs the backfill, then flips it back. Network-level access is additionally
 * restricted by the gateway's {@code /api/admin/**} IP-whitelist.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/search/backfill")
@Tag(name = "Admin: Search Backfill", description = "One-shot index maintenance jobs")
public class BackfillController {

    static final String FLAG_ES_BACKFILL = "ES_BACKFILL";

    private final BrandRatingBackfillService backfillService;
    private final FeatureFlags featureFlags;

    public BackfillController(BrandRatingBackfillService backfillService, FeatureFlags featureFlags) {
        this.backfillService = backfillService;
        this.featureFlags = featureFlags;
    }

    @PostMapping("/brand-rating")
    @Operation(summary = "Backfill brand & rating",
            description = "Runs an idempotent _update_by_query populating brand/rating on docs that lack them. "
                    + "Feature-flagged (ES_BACKFILL); returns 404 when disabled.")
    public ResponseEntity<Map<String, Object>> backfillBrandRating() throws IOException {
        if (!featureFlags.isEnabled(FLAG_ES_BACKFILL)) {
            log.warn("Rejected brand/rating backfill: ES_BACKFILL flag disabled");
            return ResponseEntity.notFound().build();
        }

        long updated = backfillService.backfill();
        return ResponseEntity.status(HttpStatus.OK)
                .body(Map.of("status", "completed", "updated", updated));
    }
}
