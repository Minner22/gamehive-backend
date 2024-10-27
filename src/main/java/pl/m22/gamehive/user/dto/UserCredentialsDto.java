package pl.m22.gamehive.user.dto;

import java.util.Set;

public record UserCredentialsDto(String email, String password, Set<String> roles) {
}
