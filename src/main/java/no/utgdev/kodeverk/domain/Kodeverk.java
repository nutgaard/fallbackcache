package no.utgdev.kodeverk.domain;

import java.util.HashMap;

public class Kodeverk {
    private HashMap<String, String> kodeverk = new HashMap<>();

    public void put(String key, String value) {
        kodeverk.put(key, value);
    }

    public String get(String key) {
        return kodeverk.get(key);
    }

    public static class KodeverkFallback extends Kodeverk {
        @Override
        public String get(String key) {
            return key;
        }
    }
}
