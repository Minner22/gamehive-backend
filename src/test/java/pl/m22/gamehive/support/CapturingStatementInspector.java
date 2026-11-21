package pl.m22.gamehive.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Przechwytuje SQL generowany przez Hibernate, żeby test mógł sprawdzić <em>kształt</em> zapytania
 * (liczbę instrukcji, brak JOIN-a), a nie tylko jego wynik.
 *
 * <p>Podpinany przez {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}
 * na poziomie konkretnej klasy testowej. Hibernate tworzy instancję refleksją, dlatego bufor jest
 * statyczny — testy korzystające z inspektora czyszczą go przed asercją ({@link #clear()}).
 */
public class CapturingStatementInspector implements StatementInspector {

    private static final List<String> STATEMENTS = new ArrayList<>();

    @Override
    public String inspect(String sql) {
        synchronized (STATEMENTS) {
            STATEMENTS.add(sql);
        }
        return sql;
    }

    public static void clear() {
        synchronized (STATEMENTS) {
            STATEMENTS.clear();
        }
    }

    public static List<String> statements() {
        synchronized (STATEMENTS) {
            return List.copyOf(STATEMENTS);
        }
    }
}
