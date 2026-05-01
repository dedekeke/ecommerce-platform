package com.ecommerce.searchservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;

/**
 * Initializes Elasticsearch indices on application startup.
 * Creates the products index with proper mappings and settings if it doesn't exist.
 */
@Component
public class ElasticsearchIndexInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);
    private static final String INDEX_NAME = "products";
    private static final String MAPPING_FILE = "elasticsearch/product-mapping.json";
    private static final String SETTINGS_FILE = "elasticsearch/product-settings.json";

    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;

    public ElasticsearchIndexInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run(String... args) {
        initializeIndex();
    }

    /**
     * Initialize the products index if it doesn't exist; otherwise attempt
     * an additive mapping update so a re-deployed service picks up the new
     * {@code nameSuggest} field used by the search-as-you-type endpoint.
     */
    public void initializeIndex() {
        try {
            logger.info("Checking if index '{}' exists...", INDEX_NAME);

            BooleanResponse exists = elasticsearchClient.indices().exists(e -> e.index(INDEX_NAME));

            if (!exists.value()) {
                logger.info("Index '{}' does not exist. Creating...", INDEX_NAME);
                createIndex();
                logger.info("Index '{}' created successfully", INDEX_NAME);
            } else {
                logger.info("Index '{}' already exists. Attempting additive mapping update for nameSuggest.",
                        INDEX_NAME);
                ensureNameSuggestField();
            }
        } catch (Exception e) {
            logger.error("Error initializing Elasticsearch index '{}': {}", INDEX_NAME, e.getMessage(), e);
            // Don't throw exception - allow application to start even if index creation fails
            // The index can be created manually or through a separate process
        }
    }

    /**
     * Issues an {@code _update_mapping} to add the {@code nameSuggest}
     * search_as_you_type field. Adding a brand-new field is always allowed by
     * Elasticsearch — type/field changes on existing fields are not, but here
     * we are strictly additive. Failure is logged and swallowed.
     */
    private void ensureNameSuggestField() {
        String body = """
                {
                  "properties": {
                    "nameSuggest": {
                      "type": "search_as_you_type",
                      "max_shingle_size": 3
                    }
                  }
                }
                """;
        try {
            elasticsearchClient.indices().putMapping(p -> p
                    .index(INDEX_NAME)
                    .withJson(new StringReader(body)));
            logger.info("Mapping update applied: nameSuggest field present on '{}'", INDEX_NAME);
        } catch (Exception ex) {
            logger.warn("Could not apply additive mapping update for nameSuggest on '{}': {}",
                    INDEX_NAME, ex.getMessage());
        }
    }

    /**
     * Create the products index with mappings and settings.
     */
    private void createIndex() throws IOException {
        // Load settings
        String settingsJson = loadResourceAsString(SETTINGS_FILE);

        // Load mappings
        String mappingsJson = loadResourceAsString(MAPPING_FILE);

        // Create the complete index configuration JSON
        String indexConfigJson = String.format("""
            {
              "settings": %s,
              "mappings": %s
            }
            """, settingsJson, mappingsJson);

        // Create index with settings and mappings
        elasticsearchClient.indices().create(c -> c
                .index(INDEX_NAME)
                .withJson(new StringReader(indexConfigJson))
        );
    }

    /**
     * Load a classpath resource as a string.
     */
    private String loadResourceAsString(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes());
        }
    }
}
