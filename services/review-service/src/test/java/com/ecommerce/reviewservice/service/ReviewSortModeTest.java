package com.ecommerce.reviewservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSortModeTest {

    @ParameterizedTest
    @CsvSource({
            "helpful,HELPFUL",
            "HELPFUL,HELPFUL",
            "recent,RECENT",
            "RECENT,RECENT",
            "rating,RATING",
            "RATING,RATING"
    })
    void should_parse_known_sort_modes(String input, ReviewSortMode expected) {
        assertThat(ReviewSortMode.from(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "  ", "asdf"})
    void should_default_to_helpful_for_invalid_or_blank_input(String input) {
        assertThat(ReviewSortMode.from(input)).isEqualTo(ReviewSortMode.HELPFUL);
    }

    @Test
    void should_expose_helpful_then_created_sort_keys() {
        assertThat(ReviewSortMode.HELPFUL.sort().getOrderFor("helpful")).isNotNull();
        assertThat(ReviewSortMode.HELPFUL.sort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void should_expose_rating_sort_key() {
        assertThat(ReviewSortMode.RATING.sort().getOrderFor("rating")).isNotNull();
    }
}
