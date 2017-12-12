package no.utgdev.kodeverk;

import no.utgdev.kodeverk.domain.Kodeverk;
import no.utgdev.kodeverk.domain.KodeverkWSDefinition;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

public class Klient {
    private final KodeverkWSDefinition kodeverkWSDefinition;
    private final ConcurrentHashMap<String, CompletableFuture<Kodeverk>> cache = new ConcurrentHashMap<>();
    private final Kodeverk.KodeverkFallback fallback = new Kodeverk.KodeverkFallback();
    private final ForkJoinPool executorPool = new ForkJoinPool(1);

    public Klient(KodeverkWSDefinition kodeverkWSDefinition) {
        this.kodeverkWSDefinition = kodeverkWSDefinition;
    }

    public Kodeverk hentKodeverk(String kodeverkRef) {
        CompletableFuture<Kodeverk> kodeverk = cache.computeIfAbsent(kodeverkRef, this::hentKodeverkFraPorttype);

        if (kodeverk.isCompletedExceptionally()) {
            return fallback;
        }
        return kodeverk.getNow(fallback);
    }

    public void refreshKodeverk(String kodeverkRef) {
        final CompletableFuture<Kodeverk> nyttKodeverk = hentKodeverkFraPorttype(kodeverkRef);
        nyttKodeverk.thenRun(() -> {
            cache.put(kodeverkRef, nyttKodeverk);
        });
    }

    private CompletableFuture<Kodeverk> hentKodeverkFraPorttype(final String kodeverkRef) {
        CompletableFuture<Kodeverk> kodeverk = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                kodeverk.complete(kodeverkWSDefinition.hentKodeverk(kodeverkRef));
            } catch (Exception e) {
                kodeverk.completeExceptionally(e);
            }
        }, executorPool);

        return kodeverk;
    }
}
