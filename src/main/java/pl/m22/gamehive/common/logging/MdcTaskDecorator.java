package pl.m22.gamehive.common.logging;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;


/**
 * Kopiuje MDC (m.in. correlationId z CorrelationIdFilter) z wątku submitującego zadanie
 * na wątek wykonujący. Snapshot robiony przy decorate() — czyli przy submitcie, gdy
 * AFTER_COMMIT leci jeszcze w wątku requestu i MDC jest wypełnione (przed MDC.remove).
 * Po zadaniu przywraca poprzedni stan MDC wątku roboczego (higiena dla pooled threads).
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {

        Map<String, String> snapshot = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previus = MDC.getCopyOfContextMap();
            setOrClear(snapshot);

            try {
                runnable.run();
            } finally {
                setOrClear(previus);
            }
        };
    }

    private static void setOrClear(Map<String, String> context) {

        if (context != null) {
            MDC.setContextMap(context);
        }
        else {
            MDC.clear();
        }
    }
}
