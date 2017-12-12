package no.utgdev.kodeverk.domain;

import java.net.SocketTimeoutException;

public class KodeverkPorttype implements KodeverkWSDefinition {
    private static Kodeverk land = new Kodeverk();
    private static Kodeverk kjonn = new Kodeverk();
    private static Kodeverk applikasjon = new Kodeverk();

    static {
        land.put("NOR", "Norge");
        land.put("DAN", "Danmark");
        land.put("FIN", "Finland");

        kjonn.put("M", "Mann");
        kjonn.put("K", "Kvinne");

        applikasjon.put("Modia", "Modiabrukerdialog");
        applikasjon.put("Diag", "Dialogstyring");
        applikasjon.put("Henv", "Henvendelse");
    }

    @Override
    public Kodeverk hentKodeverk(String kodeverkRef) throws SocketTimeoutException, InterruptedException {
        if ("land".equals(kodeverkRef)) {
            return land;
        } else if ("kjonn".equals(kodeverkRef)) {
            return kjonn;
        } else if ("applikasjon".equals(kodeverkRef)) {
            return applikasjon;
        }
        return null;
    }
}
