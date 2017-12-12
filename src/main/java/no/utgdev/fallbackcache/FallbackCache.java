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
        CompletableFuture<DATATYPE> data = cache.computeIfAbsent(request, this::hentFraFetcher);

        if (data.isCompletedExceptionally()) {
            return fallback;
        }
        return data.getNow(fallback);
    }

    public void refresh(REQUESTTYPE request) {
        final CompletableFuture<DATATYPE> newData = hentFraFetcher(request);
        newData.thenRun(() -> {
            cache.put(request, newData);
        });
    }

    private CompletableFuture<DATATYPE> hentFraFetcher(final REQUESTTYPE request) {
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
