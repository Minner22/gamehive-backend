package pl.m22.gamehive.user.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRolesDto(@NotEmpty Set<String> roles) {
}
