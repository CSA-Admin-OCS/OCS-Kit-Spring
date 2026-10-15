package com.open.spring.scraper.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScrapeResponse {
    private List<Map<String, Object>> items = new ArrayList<>();
    private Map<String, Object> meta = new HashMap<>();

    public ScrapeResponse() {
    }

    public ScrapeResponse(List<Map<String, Object>> items, Map<String, Object> meta) {
        this.items = items;
        this.meta = meta;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}
