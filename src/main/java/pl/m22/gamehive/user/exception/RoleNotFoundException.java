package pl.m22.gamehive.user.exception;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String roleName) {
        super("Role not found: " + roleName);
    }
}
