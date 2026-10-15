package com.open.spring.scraper.presets;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.open.spring.scraper.dto.ScrapeField;
import com.open.spring.scraper.dto.ScrapeRequest;

@Component
public class ScraperPresetRegistry {

    public ScrapeRequest getPreset(String presetName) {
        if (presetName == null || presetName.isBlank()) {
            return null;
        }
        if ("dallas-academicworks".equalsIgnoreCase(presetName)) {
            return dallasAcademicWorks();
        }
        return null;
    }

    private ScrapeRequest dallasAcademicWorks() {
        ScrapeRequest request = new ScrapeRequest();
        request.setUrl("https://dallascollege.academicworks.com/opportunities");
        request.setItemsSelector("table tbody tr");

        ScrapeRequest.PaginationConfig pagination = new ScrapeRequest.PaginationConfig();
        pagination.setType("optionDataDirectUrl");
        pagination.setSelector("option[name=page]");
        pagination.setAttr("data-direct-url");
        request.setPagination(pagination);

        Map<String, ScrapeField> fields = new HashMap<>();
        fields.put("name", field("a", "text", null));
        fields.put("award", field("td:nth-child(1)", "text", null));
        fields.put("deadline", field("td:nth-child(2)", "text", null));
        fields.put("link", field("a", "attr", "href"));
        request.setFields(fields);

        ScrapeRequest.DetailConfig detail = new ScrapeRequest.DetailConfig();
        detail.setLinkSelector("a");
        detail.setLinkAttr("href");
        Map<String, ScrapeField> detailFields = new HashMap<>();
        detailFields.put("questions", field(".js-question", "texts", null));
        detail.setFields(detailFields);
        request.setDetail(detail);

        ScrapeRequest.FilterConfig filters = new ScrapeRequest.FilterConfig();
        filters.setDeadlineField("deadline");
        filters.setSkipBlankDeadline(true);
        filters.setSkipEndedDeadline(true);
        filters.setEndedValue("Ended");
        request.setFilters(filters);

        ScrapeRequest.LimitsConfig limits = new ScrapeRequest.LimitsConfig();
        limits.setMaxPages(20);
        limits.setMaxItems(1000);
        limits.setMaxConcurrency(10);
        limits.setTimeoutMs(20000);
        request.setLimits(limits);
        return request;
    }

    private ScrapeField field(String selector, String mode, String attr) {
        ScrapeField field = new ScrapeField();
        field.setSelector(selector);
        field.setMode(mode);
        field.setAttr(attr);
        return field;
    }
}
