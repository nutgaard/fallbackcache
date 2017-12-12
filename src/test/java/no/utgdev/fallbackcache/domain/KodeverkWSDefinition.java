package no.utgdev.fallbackcache.domain;

import java.net.SocketTimeoutException;

public interface KodeverkWSDefinition {
    public Kodeverk hentKodeverk(String kodeverkRef) throws SocketTimeoutException, InterruptedException;
}
