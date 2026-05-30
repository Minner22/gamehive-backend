package pl.m22.gamehive.auth.jwt.service;

import org.springframework.security.core.GrantedAuthority;

import java.util.List;

public record UserAuthState(boolean enabled, List<GrantedAuthority> authorities, Long invalidAfter) {
}
