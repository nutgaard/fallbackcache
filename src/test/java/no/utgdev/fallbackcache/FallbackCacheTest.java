package no.utgdev.fallbackcache;

import no.utgdev.fallbackcache.domain.Kodeverk;
import no.utgdev.fallbackcache.domain.KodeverkPorttype;
import no.utgdev.fallbackcache.domain.KodeverkWSDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class FallbackCacheTest {

    @Test
    public void farDummyKodeverkTilAStarteMed() throws InterruptedException, SocketTimeoutException {
        Kodeverk kodeverk = new Kodeverk();
        KodeverkWSDefinition pt = mock(KodeverkWSDefinition.class);
        when(pt.hentKodeverk(anyString())).then(delay(kodeverk, 100));

        FallbackCache<String, Kodeverk> klient = new FallbackCache<>(pt::hentKodeverk, new Kodeverk.KodeverkFallback());

        // Begge kallene skjer før vi får data
        Kodeverk kodeverk1 = klient.get("land");
        Kodeverk kodeverk2 = klient.get("land");

        // Vi venter på at porttype skal kalles
        Thread.sleep(200);

        assertThat(kodeverk1.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        assertThat(kodeverk2.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        verify(pt, times(1)).hentKodeverk(anyString());
    }

    @Test
    public void farEkteKodeverkNarDetErKlart() throws InterruptedException, SocketTimeoutException {
        Kodeverk kodeverk = new Kodeverk();
        KodeverkWSDefinition pt = mock(KodeverkWSDefinition.class);
        when(pt.hentKodeverk(anyString())).then(delay(kodeverk, 100));
        FallbackCache<String, Kodeverk> klient = new FallbackCache<>(pt::hentKodeverk, new Kodeverk.KodeverkFallback());

        Kodeverk kodeverk1 = klient.get("land");

        Thread.sleep(200);
        Kodeverk kodeverk2 = klient.get("land");
        // Ekstra kall fører ikke til kall mot porttype
        klient.get("land");
        klient.get("land");

        assertThat(kodeverk1.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        assertThat(kodeverk2.getClass()).isEqualTo(Kodeverk.class);
        verify(pt, times(1)).hentKodeverk(anyString());
    }

    @Test
    public void girFallbackHvisHentingFeiler() throws InterruptedException, SocketTimeoutException {
        KodeverkWSDefinition pt = mock(KodeverkWSDefinition.class);
        when(pt.hentKodeverk(anyString())).thenThrow(SocketTimeoutException.class);
        FallbackCache<String, Kodeverk> klient = new FallbackCache<>(pt::hentKodeverk, new Kodeverk.KodeverkFallback());

        Kodeverk kodeverk1 = klient.get("land");

        // Ekstra kall her fører ikke til kall mot PortType
        klient.get("land");
        klient.get("land");

        // Vi venter på at porttype skal kalles
        Thread.sleep(100);

        assertThat(kodeverk1.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        verify(pt, times(1)).hentKodeverk(anyString());
    }

    @Test
    public void recoverEtterFeil() throws InterruptedException, SocketTimeoutException {
        KodeverkWSDefinition pt = mock(KodeverkPorttype.class);
        when(pt.hentKodeverk(anyString()))
                .thenThrow(SocketTimeoutException.class)
                .thenCallRealMethod();


        FallbackCache<String, Kodeverk> klient = new FallbackCache<>(pt::hentKodeverk, new Kodeverk.KodeverkFallback());

        // Initiell last (gir alltid fallback pga manglende data)
        Kodeverk kodeverk1 = klient.get("land");
        Thread.sleep(100);

        // Første request fikk Exception, så fortsatt fallback
        Kodeverk kodeverk2 = klient.get("land");
        klient.refresh("land");

        // Andre request funka, så nå får vi data
        Kodeverk kodeverk3 = klient.get("land");

        assertThat(kodeverk1.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        assertThat(kodeverk2.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        assertThat(kodeverk3.getClass()).isEqualTo(Kodeverk.class);
        assertThat(kodeverk1.get("NOR")).isEqualTo("NOR");
        assertThat(kodeverk2.get("NOR")).isEqualTo("NOR");
        assertThat(kodeverk3.get("NOR")).isEqualTo("Norge");
    }

    @Test
    public void beholderDataOfRefreshFeiler() throws InterruptedException, SocketTimeoutException {
        KodeverkWSDefinition pt = mock(KodeverkPorttype.class);
        when(pt.hentKodeverk(anyString()))
                .thenCallRealMethod()
                .thenThrow(SocketTimeoutException.class);

        FallbackCache<String, Kodeverk> klient = new FallbackCache<>(pt::hentKodeverk, new Kodeverk.KodeverkFallback());

        // Initiell last (gir alltid fallback pga manglende data)
        Kodeverk kodeverk1 = klient.get("land");
        Thread.sleep(100);

        // Første request fikk ok, så nå har vi data
        Kodeverk kodeverk2 = klient.get("land");
        // Refresh feiler
        klient.refresh("land");
        Thread.sleep(100);

        // Da refresh feiler så beholder vi data
        Kodeverk kodeverk3 = klient.get("land");
        Thread.sleep(1000);

        assertThat(kodeverk1.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        assertThat(kodeverk2.getClass()).isEqualTo(Kodeverk.class);
        assertThat(kodeverk3.getClass()).isEqualTo(Kodeverk.class);
        assertThat(kodeverk1.get("NOR")).isEqualTo("NOR");
        assertThat(kodeverk2.get("NOR")).isEqualTo("Norge");
        assertThat(kodeverk3.get("NOR")).isEqualTo("Norge");
    }

    @Test
    public void enRequestOmGangen() throws InterruptedException {
        final int numberOfTries = 10;
        final CountDownLatch latch = new CountDownLatch(numberOfTries);
        final AtomicInteger currentActive = new AtomicInteger();
        final AtomicInteger startCount = new AtomicInteger(1);
        final AtomicInteger endCount = new AtomicInteger(0);
        final AtomicBoolean isOk = new AtomicBoolean(true);

        KodeverkWSDefinition pt = new KodeverkWSDefinition() {
            @Override
            public Kodeverk hentKodeverk(String kodeverkRef) throws SocketTimeoutException, InterruptedException {
                int currentCount = currentActive.incrementAndGet();
                if (currentCount > 1) {
                    isOk.set(false);
                    startCount.set(currentCount);
                }

                Thread.sleep(200);

                currentCount = currentActive.decrementAndGet();
                if (currentCount != 0) {
                    isOk.set(false);
                    endCount.set(currentCount);
                }
                latch.countDown();
                return null;
            }
        };

        FallbackCache<String, Kodeverk> klient = new FallbackCache<>(pt::hentKodeverk, new Kodeverk.KodeverkFallback());
        ForkJoinPool pool = new ForkJoinPool(10);
        for (int i = 0; i < numberOfTries; i++) {
            final String id = String.valueOf(i);
            pool.submit(() -> {
                klient.get(id);
            });
        }

        latch.await();

        assertThat(currentActive.get()).isEqualTo(0);
        assertThat(startCount.get()).isEqualTo(1);
        assertThat(endCount.get()).isEqualTo(0);
        assertThat(isOk.get()).isTrue();
    }

    private Answer<Kodeverk> delay(final Kodeverk kodeverk, final long delay) {
        return new Answer<Kodeverk>() {
            public Kodeverk answer(InvocationOnMock invocationOnMock) throws Throwable {
                Thread.sleep(delay);
                return kodeverk;
            }
        };
    }
}
