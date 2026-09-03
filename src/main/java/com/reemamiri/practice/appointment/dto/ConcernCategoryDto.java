package com.reemamiri.practice.appointment.dto;

import java.util.UUID;

public record ConcernCategoryDto(UUID id, String name, String slug, String description) {}
