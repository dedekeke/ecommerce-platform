package com.ecommerce.searchservice.admin;

import com.ecommerce.common.featureflag.FeatureFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BackfillController} focusing on the feature-flag gate.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BackfillController")
class BackfillControllerTest {

    @Mock
    private BrandRatingBackfillService backfillService;

    @Mock
    private FeatureFlags featureFlags;

    private BackfillController controller;

    @BeforeEach
    void setUp() {
        controller = new BackfillController(backfillService, featureFlags);
    }

    @Test
    @DisplayName("should_return404AndNotRun_when_flagDisabled")
    void should_return404AndNotRun_when_flagDisabled() throws IOException {
        when(featureFlags.isEnabled(BackfillController.FLAG_ES_BACKFILL)).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.backfillBrandRating();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(backfillService);
    }

    @Test
    @DisplayName("should_runBackfillAndReturnCount_when_flagEnabled")
    void should_runBackfillAndReturnCount_when_flagEnabled() throws IOException {
        when(featureFlags.isEnabled(BackfillController.FLAG_ES_BACKFILL)).thenReturn(true);
        when(backfillService.backfill()).thenReturn(42L);

        ResponseEntity<Map<String, Object>> response = controller.backfillBrandRating();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "completed");
        assertThat(response.getBody()).containsEntry("updated", 42L);
        verify(backfillService).backfill();
    }
}
