package com.ecommerce.searchservice.admin;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.InlineScript;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Backfills the {@code brand} and {@code rating} fields that were added to the
 * products index mapping after documents had already been indexed.
 *
 * <p>Runs a single Elasticsearch {@code _update_by_query}:
 * <ul>
 *   <li><strong>Scope.</strong> Only documents missing {@code brand} OR
 *       {@code rating} are touched (a {@code bool.should} of negated
 *       {@code exists} queries). Documents already carrying both fields are
 *       skipped, which makes the operation <em>idempotent</em> — a second run
 *       updates nothing.</li>
 *   <li><strong>Derivation.</strong> {@code brand} is derived from the SKU
 *       prefix (the segment before the first {@code '-'}, upper-cased) since
 *       that is the source of brand identity already present on the document;
 *       {@code rating} defaults to {@code 0.0} so range facets have a value to
 *       bucket. Both are only written when absent, so existing values are never
 *       clobbered.</li>
 * </ul>
 */
@Slf4j
@Service
public class BrandRatingBackfillService {

    static final String INDEX = "products";

    /**
     * Painless script: set brand from the SKU prefix and rating to 0.0, but
     * only for fields that are currently absent. Guarded field-by-field so the
     * script itself is idempotent even if the query scope widens.
     */
    private static final String BACKFILL_SCRIPT = """
            if (ctx._source.brand == null) {
              if (ctx._source.sku != null) {
                int dash = ctx._source.sku.indexOf('-');
                ctx._source.brand = (dash > 0 ? ctx._source.sku.substring(0, dash) : ctx._source.sku).toUpperCase();
              } else {
                ctx._source.brand = 'UNKNOWN';
              }
            }
            if (ctx._source.rating == null) {
              ctx._source.rating = 0.0;
            }
            """;

    private final ElasticsearchClient elasticsearchClient;

    public BrandRatingBackfillService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * Execute the backfill.
     *
     * @return the number of documents updated by this run (0 on a re-run once
     *         every document already has both fields).
     */
    public long backfill() throws IOException {
        Query missingBrandOrRating = Query.of(q -> q.bool(BoolQuery.of(b -> b
                .minimumShouldMatch("1")
                .should(s -> s.bool(nb -> nb.mustNot(mn -> mn.exists(ExistsQuery.of(e -> e.field("brand"))))))
                .should(s -> s.bool(nb -> nb.mustNot(mn -> mn.exists(ExistsQuery.of(e -> e.field("rating")))))))));

        Script script = Script.of(s -> s.inline(InlineScript.of(i -> i
                .lang("painless")
                .source(BACKFILL_SCRIPT))));

        UpdateByQueryRequest request = UpdateByQueryRequest.of(u -> u
                .index(INDEX)
                .query(missingBrandOrRating)
                .script(script)
                .conflicts(Conflicts.Proceed)
                .refresh(true));

        UpdateByQueryResponse response = elasticsearchClient.updateByQuery(request);
        long updated = response.updated() == null ? 0L : response.updated();
        log.info("brand/rating backfill updated {} document(s)", updated);
        return updated;
    }
}
