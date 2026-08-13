package pl.m22.gamehive.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pl.m22.gamehive.common.logging.CorrelationIdFilter;
import pl.m22.gamehive.game.search.event.SearchIndexEvent;
import pl.m22.gamehive.game.search.event.SearchIndexListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Executor produkcyjny ma @Profile("!test"), więc w suicie nie istnieje jako bean — budujemy go
 * wprost z @Configuration, żeby sprawdzić realną konfigurację puli, a nie inline'owy zamiennik
 * z TestAsyncConfig.
 */
class SearchIndexExecutorTest {

    private final ThreadPoolTaskExecutor executor =
            (ThreadPoolTaskExecutor) new AsyncConfig().searchIndexExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdown();
        MDC.clear();
    }

    @Test
    @DisplayName("zadanie indeksujące biegnie na wątku puli i widzi correlationId wątku zlecającego")
    void searchIndexExecutor_runsOffCallerThreadWithCorrelationId() throws InterruptedException {

        MDC.put(CorrelationIdFilter.CORRELATION_ID, "corr-42");
        AtomicReference<String> seenCorrelationId = new AtomicReference<>();
        AtomicReference<String> workerThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        executor.execute(() -> {
            seenCorrelationId.set(MDC.get(CorrelationIdFilter.CORRELATION_ID));
            workerThread.set(Thread.currentThread().getName());
            done.countDown();
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seenCorrelationId.get()).isEqualTo("corr-42");
        assertThat(workerThread.get())
                .startsWith("search-index-")
                .isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("pula jest jednowątkowa — FIFO gwarantuje kolejność UPSERT/REMOVE tego samego dokumentu")
    void searchIndexExecutor_isSingleThreaded() {

        assertThat(executor.getCorePoolSize()).isOne();
        assertThat(executor.getMaxPoolSize()).isOne();
    }

    /**
     * Najważniejsze twierdzenie tego designu: przy pełnej kolejce zadanie jest PORZUCANE, a nie
     * wykonywane przez wątek zlecający (CallerRunsPolicy) ani zgłaszane wyjątkiem — inaczej
     * indeksowanie wracałoby na wątek moderatora albo wyjątek leciałby z AFTER_COMMIT.
     */
    @Test
    @DisplayName("przepełniona kolejka -> zadanie porzucone po cichu, wątek zlecający nie wykonuje niczego ani nie dostaje wyjątku")
    void searchIndexExecutor_whenQueueIsFull_dropsTaskWithoutTouchingCaller() throws InterruptedException {

        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger ranOnCallerThread = new AtomicInteger();
        String callerThread = Thread.currentThread().getName();

        executor.execute(() -> {                       // zajmuje jedyny wątek puli
            workerBlocked.countDown();
            awaitQuietly(releaseWorker);
        });
        assertThat(workerBlocked.await(5, TimeUnit.SECONDS)).isTrue();

        Runnable countIfOnCaller = () -> {
            if (Thread.currentThread().getName().equals(callerThread)) {
                ranOnCallerThread.incrementAndGet();
            }
        };

        assertThatNoException().isThrownBy(() -> {
            for (int task = 0; task < 502; task++) {   // 500 mieści się w kolejce, reszta musi zostać odrzucona
                executor.execute(countIfOnCaller);
            }
        });
        assertThat(ranOnCallerThread).hasValue(0);

        releaseWorker.countDown();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Adnotacja zamiast pomiaru czasu: to jedyne deterministyczne przypięcie kryterium „decyzja
     * moderacyjna nie czeka na Meili" — w profilu test executor jest inline, a realny pomiar byłby
     * niestabilny. Ten sam duch, co OpenApiDocumentationTest: smoke-test wpięcia.
     */
    @Test
    @DisplayName("listener indeksu jest @Async na searchIndexExecutor — indeksowanie nie może wrócić na wątek requestu")
    void searchIndexListener_isAsyncOnSearchIndexExecutor() throws NoSuchMethodException {

        Async async = SearchIndexListener.class
                .getDeclaredMethod("onSearchIndex", SearchIndexEvent.class)
                .getAnnotation(Async.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo(AsyncConfig.SEARCH_INDEX_EXECUTOR);
    }
}
