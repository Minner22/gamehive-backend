package pl.m22.gamehive.common.logging;

public class LoggingUtils {
    public static String obfuscateEmail(String email) {
        return email.replaceAll("(?<=.{2}).(?=.*@)", "*");
    }
}
