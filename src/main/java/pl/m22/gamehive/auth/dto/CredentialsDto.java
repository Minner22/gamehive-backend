package pl.m22.gamehive.auth.dto;

import java.util.Set;

public record CredentialsDto(String email, String password, Set<String> roles) {
}
