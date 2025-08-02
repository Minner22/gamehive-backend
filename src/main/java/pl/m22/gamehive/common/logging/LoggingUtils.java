package pl.m22.gamehive.common.logging;

public class LoggingUtils {

    private LoggingUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String obfuscateEmail(String email) {
        return email.replaceAll("(?<=.{2}).(?=.*@)", "*");
    }
}
