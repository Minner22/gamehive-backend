package pl.m22.gamehive.user.event;

import java.util.UUID;

public record UserDeletedEvent(String email, UUID userId) {
}
