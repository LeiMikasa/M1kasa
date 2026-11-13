package com.m1kasa.search.api.dto;

import java.util.List;

public record ReviewSearchRequest(String question, List<Long> shopIds) {
}
