package pl.m22.gamehive.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private static final String CORRELATION_ID = "correlationId";

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("decorate() -> correlationId z wątku submitującego jest widoczny na wątku wykonującym")
    void decorate_propagatesMdcToWorkerThread() throws InterruptedException {

        MDC.put(CORRELATION_ID, "abc-123");
        AtomicReference<String> seenOnWorker = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> seenOnWorker.set(MDC.get(CORRELATION_ID)));

        Thread worker = new Thread(decorated);   // inny wątek niż submitujący
        worker.start();
        worker.join();

        assertThat(seenOnWorker.get()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("decorate() -> snapshot robiony przy submitcie; późniejsze MDC.clear() nie wpływa na zadanie")
    void decorate_usesSnapshotFromSubmitTime() throws InterruptedException {

        MDC.put(CORRELATION_ID, "snapshot-1");
        AtomicReference<String> seenOnWorker = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> seenOnWorker.set(MDC.get(CORRELATION_ID)));

        MDC.clear();   // symuluje MDC.remove w CorrelationIdFilter po zakończeniu requestu

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(seenOnWorker.get()).isEqualTo("snapshot-1");
    }

    @Test
    @DisplayName("decorate() -> MDC wątku roboczego jest czyszczone po zadaniu (higiena pooled threads)")
    void decorate_restoresWorkerMdcAfterRun() throws InterruptedException {

        MDC.put(CORRELATION_ID, "abc-123");
        Runnable decorated = decorator.decorate(() -> { /* no-op */ });

        AtomicReference<String> leftBehind = new AtomicReference<>("sentinel");
        Thread worker = new Thread(() -> {
            decorated.run();
            leftBehind.set(MDC.get(CORRELATION_ID));   // wątek roboczy startuje z pustym MDC
        });
        worker.start();
        worker.join();

        assertThat(leftBehind.get()).isNull();
    }
}