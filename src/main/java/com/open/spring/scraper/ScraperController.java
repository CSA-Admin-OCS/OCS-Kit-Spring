package com.open.spring.scraper;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.open.spring.scraper.dto.ScrapeRequest;
import com.open.spring.scraper.dto.ScrapeResponse;
import com.open.spring.scraper.presets.ScraperPresetRegistry;
import com.open.spring.scraper.security.UrlSafetyValidator;

@RestController
@RequestMapping("/api/scraper")
public class ScraperController {
    private final GenericScraperService genericScraperService;
    private final ScraperPresetRegistry presetRegistry;
    private final UrlSafetyValidator urlSafetyValidator;

    public ScraperController(
            GenericScraperService genericScraperService,
            ScraperPresetRegistry presetRegistry,
            UrlSafetyValidator urlSafetyValidator
    ) {
        this.genericScraperService = genericScraperService;
        this.presetRegistry = presetRegistry;
        this.urlSafetyValidator = urlSafetyValidator;
    }

    @PostMapping("/run")
    public ScrapeResponse run(@RequestBody ScrapeRequest request) {
        ScrapeRequest merged = mergePresetAndOverrides(request);
        validateRequest(merged);
        return genericScraperService.run(merged);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private ScrapeRequest mergePresetAndOverrides(ScrapeRequest request) {
        if (request.getPreset() == null || request.getPreset().isBlank()) {
            return request;
        }

        ScrapeRequest preset = presetRegistry.getPreset(request.getPreset());
        if (preset == null) {
            throw new IllegalArgumentException("Unknown preset: " + request.getPreset());
        }

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            preset.setUrl(request.getUrl());
        }
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            preset.setHeaders(request.getHeaders());
        }
        if (request.getLimits() != null) {
            preset.setLimits(request.getLimits());
        }
        if (request.getFilters() != null && request.getFilters().getDeadlineField() != null) {
            preset.setFilters(request.getFilters());
        }
        if (request.getOverrides() != null) {
            if (request.getOverrides().getItemsSelector() != null && !request.getOverrides().getItemsSelector().isBlank()) {
                preset.setItemsSelector(request.getOverrides().getItemsSelector());
            }
            if (request.getOverrides().getPagination() != null) {
                preset.setPagination(request.getOverrides().getPagination());
            }
            if (request.getOverrides().getDetail() != null) {
                preset.setDetail(request.getOverrides().getDetail());
            }
            if (request.getOverrides().getFields() != null && !request.getOverrides().getFields().isEmpty()) {
                preset.setFields(request.getOverrides().getFields());
            }
        }

        if (request.getItemsSelector() != null && !request.getItemsSelector().isBlank()) {
            preset.setItemsSelector(request.getItemsSelector());
        }
        if (request.getPagination() != null) {
            preset.setPagination(request.getPagination());
        }
        if (request.getDetail() != null) {
            preset.setDetail(request.getDetail());
        }
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            preset.setFields(request.getFields());
        }

        return preset;
    }

    private void validateRequest(ScrapeRequest request) {
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new IllegalArgumentException("url is required (or supplied by preset)");
        }
        if (request.getItemsSelector() == null || request.getItemsSelector().isBlank()) {
            throw new IllegalArgumentException("itemsSelector is required");
        }
        if (request.getFields() == null || request.getFields().isEmpty()) {
            throw new IllegalArgumentException("fields are required");
        }

        urlSafetyValidator.validateTargetUrl(request.getUrl());
        urlSafetyValidator.validateHeaders(request.getHeaders().keySet());

        ScrapeRequest.LimitsConfig limits = request.getLimits();
        if (limits.getMaxPages() <= 0 || limits.getMaxPages() > 100) {
            throw new IllegalArgumentException("maxPages must be between 1 and 100");
        }
        if (limits.getMaxItems() <= 0 || limits.getMaxItems() > 5000) {
            throw new IllegalArgumentException("maxItems must be between 1 and 5000");
        }
        if (limits.getMaxConcurrency() <= 0 || limits.getMaxConcurrency() > 16) {
            throw new IllegalArgumentException("maxConcurrency must be between 1 and 16");
        }
        if (limits.getTimeoutMs() < 1000 || limits.getTimeoutMs() > 60000) {
            throw new IllegalArgumentException("timeoutMs must be between 1000 and 60000");
        }
    }
}
