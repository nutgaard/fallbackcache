package no.utgdev.fallbackcache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

public class FallbackCache<REQUESTTYPE, DATATYPE> {
    private final Fetcher<REQUESTTYPE, DATATYPE> fetcher;
    private final DATATYPE fallback;
    private final ConcurrentHashMap<REQUESTTYPE, CompletableFuture<DATATYPE>> cache = new ConcurrentHashMap<>();
    private final ForkJoinPool executorPool = new ForkJoinPool(1);

    public FallbackCache(Fetcher<REQUESTTYPE, DATATYPE> fetcher, DATATYPE fallback) {
        this.fetcher = fetcher;
        this.fallback = fallback;
    }

    public DATATYPE get(REQUESTTYPE request) {
        CompletableFuture<DATATYPE> data = cache.computeIfAbsent(request, this::getFromFetcher);

        if (data.isCompletedExceptionally()) {
            return fallback;
        }
        return data.getNow(fallback);
    }

    public void refresh(REQUESTTYPE request) {
        final CompletableFuture<DATATYPE> newData = getFromFetcher(request);
        newData.thenRun(() -> {
            cache.put(request, newData);
        });
    }

    public void fix() {
        this.cache.entrySet()
                .stream()
                .filter((entry) -> entry.getValue().isCompletedExceptionally())
                .forEach((entry) -> this.refresh(entry.getKey()));
    }

    private CompletableFuture<DATATYPE> getFromFetcher(final REQUESTTYPE request) {
        CompletableFuture<DATATYPE> data = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                data.complete(fetcher.fetch(request));
            } catch (Exception e) {
                data.completeExceptionally(e);
            }
        }, executorPool);

        return data;
    }

    public interface Fetcher<REQUESTTYPE, DATATYPE> {
        DATATYPE fetch(REQUESTTYPE request) throws Exception;
    }
}
