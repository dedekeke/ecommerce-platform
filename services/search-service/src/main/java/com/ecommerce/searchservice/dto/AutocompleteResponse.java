package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Autocomplete response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutocompleteResponse {
    
    private List<String> suggestions;
}
