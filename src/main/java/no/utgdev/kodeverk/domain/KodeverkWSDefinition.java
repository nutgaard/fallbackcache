package no.utgdev.kodeverk.domain;

import java.net.SocketTimeoutException;

public interface KodeverkWSDefinition {
    public Kodeverk hentKodeverk(String kodeverkRef) throws SocketTimeoutException, InterruptedException;
}
