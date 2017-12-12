package no.utgdev.kodeverk.klient;

import no.utgdev.kodeverk.Klient;
import no.utgdev.kodeverk.domain.Kodeverk;
import no.utgdev.kodeverk.domain.KodeverkPorttype;
import no.utgdev.kodeverk.domain.KodeverkWSDefinition;
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

public class SimpleTest {

    @Test
    public void farDummyKodeverkTilAStarteMed() throws InterruptedException, SocketTimeoutException {
        Kodeverk kodeverk = new Kodeverk();
        KodeverkWSDefinition pt = mock(KodeverkWSDefinition.class);
        when(pt.hentKodeverk(anyString())).then(delay(kodeverk, 100));

        Klient klient = new Klient(pt);

        // Begge kallene skjer før vi får data
        Kodeverk kodeverk1 = klient.hentKodeverk("land");
        Kodeverk kodeverk2 = klient.hentKodeverk("land");

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
        Klient klient = new Klient(pt);

        Kodeverk kodeverk1 = klient.hentKodeverk("land");

        Thread.sleep(200);
        Kodeverk kodeverk2 = klient.hentKodeverk("land");
        // Ekstra kall fører ikke til kall mot porttype
        klient.hentKodeverk("land");
        klient.hentKodeverk("land");

        assertThat(kodeverk1.getClass()).isEqualTo(Kodeverk.KodeverkFallback.class);
        assertThat(kodeverk2.getClass()).isEqualTo(Kodeverk.class);
        verify(pt, times(1)).hentKodeverk(anyString());
    }

    @Test
    public void girFallbackHvisHentingFeiler() throws InterruptedException, SocketTimeoutException {
        KodeverkWSDefinition pt = mock(KodeverkWSDefinition.class);
        when(pt.hentKodeverk(anyString())).thenThrow(SocketTimeoutException.class);
        Klient klient = new Klient(pt);

        Kodeverk kodeverk1 = klient.hentKodeverk("land");

        // Ekstra kall her fører ikke til kall mot PortType
        klient.hentKodeverk("land");
        klient.hentKodeverk("land");

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


        Klient klient = new Klient(pt);

        // Initiell last (gir alltid fallback pga manglende data)
        Kodeverk kodeverk1 = klient.hentKodeverk("land");
        Thread.sleep(100);

        // Første request fikk Exception, så fortsatt fallback
        Kodeverk kodeverk2 = klient.hentKodeverk("land");
        klient.refreshKodeverk("land");
        Thread.sleep(100);

        // Andre request funka, så nå får vi data
        Kodeverk kodeverk3 = klient.hentKodeverk("land");

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

        Klient klient = new Klient(pt);

        // Initiell last (gir alltid fallback pga manglende data)
        Kodeverk kodeverk1 = klient.hentKodeverk("land");
        Thread.sleep(100);

        // Første request fikk ok, så nå har vi data
        Kodeverk kodeverk2 = klient.hentKodeverk("land");
        // Refresh feiler
        klient.refreshKodeverk("land");
        Thread.sleep(100);

        // Da refresh feiler så beholder vi data
        Kodeverk kodeverk3 = klient.hentKodeverk("land");
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

        Klient klient = new Klient(pt);
        ForkJoinPool pool = new ForkJoinPool(10);
        for (int i = 0; i < numberOfTries; i++) {
            final String id = String.valueOf(i);
            pool.submit(() -> {
                klient.hentKodeverk(id);
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
