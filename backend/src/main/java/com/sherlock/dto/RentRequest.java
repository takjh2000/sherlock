package com.sherlock.dto;

import jakarta.validation.constraints.NotNull;

public record RentRequest(@NotNull Long gameId) {
}
