package com.open.spring.scraper.dto;

import java.util.HashMap;
import java.util.Map;

public class ScrapeRequest {
    private String url;
    private String preset;
    private Map<String, String> headers = new HashMap<>();
    private PaginationConfig pagination;
    private String itemsSelector;
    private Map<String, ScrapeField> fields = new HashMap<>();
    private DetailConfig detail;
    private FilterConfig filters = new FilterConfig();
    private LimitsConfig limits = new LimitsConfig();
    private OverrideConfig overrides;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new HashMap<>() : headers;
    }

    public PaginationConfig getPagination() {
        return pagination;
    }

    public void setPagination(PaginationConfig pagination) {
        this.pagination = pagination;
    }

    public String getItemsSelector() {
        return itemsSelector;
    }

    public void setItemsSelector(String itemsSelector) {
        this.itemsSelector = itemsSelector;
    }

    public Map<String, ScrapeField> getFields() {
        return fields;
    }

    public void setFields(Map<String, ScrapeField> fields) {
        this.fields = fields == null ? new HashMap<>() : fields;
    }

    public DetailConfig getDetail() {
        return detail;
    }

    public void setDetail(DetailConfig detail) {
        this.detail = detail;
    }

    public FilterConfig getFilters() {
        return filters;
    }

    public void setFilters(FilterConfig filters) {
        this.filters = filters == null ? new FilterConfig() : filters;
    }

    public LimitsConfig getLimits() {
        return limits;
    }

    public void setLimits(LimitsConfig limits) {
        this.limits = limits == null ? new LimitsConfig() : limits;
    }

    public OverrideConfig getOverrides() {
        return overrides;
    }

    public void setOverrides(OverrideConfig overrides) {
        this.overrides = overrides;
    }

    public static class PaginationConfig {
        private String type;
        private String selector;
        private String attr = "href";
        private String queryParam;
        private Integer startPage = 1;
        private Integer endPage;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSelector() {
            return selector;
        }

        public void setSelector(String selector) {
            this.selector = selector;
        }

        public String getAttr() {
            return attr;
        }

        public void setAttr(String attr) {
            this.attr = attr;
        }

        public String getQueryParam() {
            return queryParam;
        }

        public void setQueryParam(String queryParam) {
            this.queryParam = queryParam;
        }

        public Integer getStartPage() {
            return startPage;
        }

        public void setStartPage(Integer startPage) {
            this.startPage = startPage;
        }

        public Integer getEndPage() {
            return endPage;
        }

        public void setEndPage(Integer endPage) {
            this.endPage = endPage;
        }
    }

    public static class DetailConfig {
        private String linkSelector;
        private String linkAttr = "href";
        private Map<String, ScrapeField> fields = new HashMap<>();

        public String getLinkSelector() {
            return linkSelector;
        }

        public void setLinkSelector(String linkSelector) {
            this.linkSelector = linkSelector;
        }

        public String getLinkAttr() {
            return linkAttr;
        }

        public void setLinkAttr(String linkAttr) {
            this.linkAttr = linkAttr;
        }

        public Map<String, ScrapeField> getFields() {
            return fields;
        }

        public void setFields(Map<String, ScrapeField> fields) {
            this.fields = fields == null ? new HashMap<>() : fields;
        }
    }

    public static class FilterConfig {
        private String deadlineField;
        private boolean skipBlankDeadline = false;
        private boolean skipEndedDeadline = false;
        private String endedValue = "Ended";

        public String getDeadlineField() {
            return deadlineField;
        }

        public void setDeadlineField(String deadlineField) {
            this.deadlineField = deadlineField;
        }

        public boolean isSkipBlankDeadline() {
            return skipBlankDeadline;
        }

        public void setSkipBlankDeadline(boolean skipBlankDeadline) {
            this.skipBlankDeadline = skipBlankDeadline;
        }

        public boolean isSkipEndedDeadline() {
            return skipEndedDeadline;
        }

        public void setSkipEndedDeadline(boolean skipEndedDeadline) {
            this.skipEndedDeadline = skipEndedDeadline;
        }

        public String getEndedValue() {
            return endedValue;
        }

        public void setEndedValue(String endedValue) {
            this.endedValue = endedValue;
        }
    }

    public static class LimitsConfig {
        private int maxPages = 5;
        private int maxItems = 300;
        private int maxConcurrency = 8;
        private int timeoutMs = 15000;

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }

        public int getMaxItems() {
            return maxItems;
        }

        public void setMaxItems(int maxItems) {
            this.maxItems = maxItems;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class OverrideConfig {
        private DetailConfig detail;
        private PaginationConfig pagination;
        private String itemsSelector;
        private Map<String, ScrapeField> fields = new HashMap<>();

        public DetailConfig getDetail() {
            return detail;
        }

        public void setDetail(DetailConfig detail) {
            this.detail = detail;
        }

        public PaginationConfig getPagination() {
            return pagination;
        }

        public void setPagination(PaginationConfig pagination) {
            this.pagination = pagination;
        }

        public String getItemsSelector() {
            return itemsSelector;
        }

        public void setItemsSelector(String itemsSelector) {
            this.itemsSelector = itemsSelector;
        }

        public Map<String, ScrapeField> getFields() {
            return fields;
        }

        public void setFields(Map<String, ScrapeField> fields) {
            this.fields = fields == null ? new HashMap<>() : fields;
        }
    }
}
