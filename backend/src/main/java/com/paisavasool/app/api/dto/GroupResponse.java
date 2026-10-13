package com.paisavasool.app.api.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(UUID id, String name, Instant createdAt) {
}
