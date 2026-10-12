import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Ass im Ärmel – Homework 1, Group 10
 *
 * Ein Spieler spielt gegen einen Dealer (Computer).
 * Ablauf einer Runde:
 *   1. Blind von 10 € setzen
 *   2. Spieler und Dealer bekommen je eine Karte, nur die Spieler-Karte ist sichtbar
 *   3. Spieler wählt einen Zusatzeinsatz (Chips 1, 5, 10, 20, 50 €)
 *   4. Dealer entscheidet per Zufallszahl, ob er mitgeht
 *   5. Karten vergleichen, Guthaben berechnen, alles speichern
 *
 * Starten:  java Kartenduell.java
 * Tests:    java Kartenduell.java --test
 */
public class Kartenduell {

    // ------------------------------------------------------------------
    // Konstanten
    // ------------------------------------------------------------------
    static final int BLIND = 10;
    static final int STARTGUTHABEN = 100;
    static final int[] CHIPS = {1, 5, 10, 20, 50};
    static final int MAX_ABZUG = 30;

    /** Grundwahrscheinlichkeit (in %), dass der Dealer mitgeht – Index = Kartenwert. */
    static final int[] GRUNDWAHRSCHEINLICHKEIT = new int[15];
    static {
        GRUNDWAHRSCHEINLICHKEIT[14] = 100; // Ass
        GRUNDWAHRSCHEINLICHKEIT[13] = 90;  // König
        GRUNDWAHRSCHEINLICHKEIT[12] = 85;  // Dame
        GRUNDWAHRSCHEINLICHKEIT[11] = 80;  // Bube
        GRUNDWAHRSCHEINLICHKEIT[10] = 75;
        GRUNDWAHRSCHEINLICHKEIT[9]  = 65;
        GRUNDWAHRSCHEINLICHKEIT[8]  = 60;
        GRUNDWAHRSCHEINLICHKEIT[7]  = 55;
        GRUNDWAHRSCHEINLICHKEIT[6]  = 45;
        GRUNDWAHRSCHEINLICHKEIT[5]  = 40;
        GRUNDWAHRSCHEINLICHKEIT[4]  = 25;
        GRUNDWAHRSCHEINLICHKEIT[3]  = 15;
        GRUNDWAHRSCHEINLICHKEIT[2]  = 0;
    }

    static final SecureRandom ZUFALL = new SecureRandom();

    // ------------------------------------------------------------------
    // Karte
    // ------------------------------------------------------------------
    record Karte(int wert, String farbe) {
        String name() {
            return switch (wert) {
                case 11 -> "Bube";
                case 12 -> "Dame";
                case 13 -> "König";
                case 14 -> "Ass";
                default -> String.valueOf(wert);
            };
        }
        @Override public String toString() { return farbe + " " + name(); }
    }

    /** Function 3: zwei verschiedene Karten aus einem 52er-Deck ziehen. */
    static Karte[] zieheZweiKarten() {
        String[] farben = {"Pik", "Herz", "Karo", "Kreuz"};
        List<Karte> deck = new ArrayList<>();
        for (String f : farben)
            for (int w = 2; w <= 14; w++) deck.add(new Karte(w, f));
        Collections.shuffle(deck, ZUFALL);
        return new Karte[]{deck.get(0), deck.get(1)}; // [0] = Spieler, [1] = Dealer
    }

    // ------------------------------------------------------------------
    // Spiellogik (ohne Ein-/Ausgabe, damit sie testbar ist)
    // ------------------------------------------------------------------

    /** Function 5, Schritt 1+2: Wahrscheinlichkeit, dass der Dealer mitgeht. */
    static int mitgehWahrscheinlichkeit(int dealerWert, int zusatzeinsatz) {
        if (dealerWert == 14) return 100;                              // Ass: kein Abzug
        int abzug = Math.min(MAX_ABZUG, (zusatzeinsatz / 10) * 2);     // -2 % pro volle 10 €
        return Math.max(0, GRUNDWAHRSCHEINLICHKEIT[dealerWert] - abzug);
    }

    record Rundenergebnis(int wahrscheinlichkeit, Integer zufallszahl, boolean dealerGehtMit,
                          String ergebnis, int gesamteinsatz, int auszahlung, int betrag, int neuesGuthaben) {}

    /**
     * Function 5–7: Dealer-Entscheidung, Kartenvergleich und Abrechnung.
     * Die Zufallszahl wird übergeben, damit man die Methode mit festen Werten testen kann.
     */
    static Rundenergebnis berechneRunde(int guthaben, int spielerWert, int dealerWert,
                                        int zusatzeinsatz, int zufallszahl) {
        int gesamteinsatz = BLIND + zusatzeinsatz;

        // Function 5: Entscheidung des Dealers
        int wahrscheinlichkeit = 100;
        Integer gezogen = null;
        boolean gehtMit = true;                 // ohne Zusatzeinsatz geht der Dealer immer mit
        if (zusatzeinsatz > 0) {
            wahrscheinlichkeit = mitgehWahrscheinlichkeit(dealerWert, zusatzeinsatz);
            gezogen = zufallszahl;
            gehtMit = zufallszahl <= wahrscheinlichkeit;
        }

        // Function 6 + 7: Ergebnis und Auszahlung
        String ergebnis;
        int auszahlung;
        if (!gehtMit)                       { ergebnis = "FOLD"; auszahlung = gesamteinsatz + BLIND; }
        else if (spielerWert > dealerWert)  { ergebnis = "WIN";  auszahlung = 2 * gesamteinsatz; }
        else if (spielerWert < dealerWert)  { ergebnis = "LOSE"; auszahlung = 0; }
        else                                { ergebnis = "DRAW"; auszahlung = gesamteinsatz; }

        int betrag = auszahlung - gesamteinsatz;
        return new Rundenergebnis(wahrscheinlichkeit, gezogen, gehtMit, ergebnis,
                gesamteinsatz, auszahlung, betrag, guthaben + betrag);
    }

    // ------------------------------------------------------------------
    // Datenspeicher: Tabellen user_data und game_history
    // Jede Tabelle wird als CSV-Datei gespeichert (eine Zeile = ein Datensatz).
    // ------------------------------------------------------------------
    static class Datenbank {
        private final Path userData;
        private final Path gameHistory;
        private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        Datenbank(Path ordner) throws IOException {
            Files.createDirectories(ordner);
            userData = ordner.resolve("user_data.csv");
            gameHistory = ordner.resolve("game_history.csv");
            if (Files.notExists(userData))
                Files.writeString(userData, "username;balance\n", StandardCharsets.UTF_8);
            if (Files.notExists(gameHistory))
                Files.writeString(gameHistory,
                        "zeit;username;spieler_karte;dealer_karte;blind;zusatzeinsatz;ergebnis;betrag\n",
                        StandardCharsets.UTF_8);
        }

        /** FR-02: liefert das Guthaben oder null, wenn der Spieler nicht existiert. */
        Integer ladeGuthaben(String username) throws IOException {
            List<String> zeilen = Files.readAllLines(userData, StandardCharsets.UTF_8);
            for (int i = 1; i < zeilen.size(); i++) {
                String[] t = zeilen.get(i).split(";");
                if (t[0].equalsIgnoreCase(username)) return Integer.parseInt(t[1]);
            }
            return null;
        }

        /** FR-03: neuen Spieler anlegen. */
        void neuerSpieler(String username) throws IOException {
            Files.writeString(userData, username + ";" + STARTGUTHABEN + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }

        /** FR-24/26/27: Guthaben aktualisieren. */
        void speichereGuthaben(String username, int guthaben) throws IOException {
            List<String> zeilen = new ArrayList<>(Files.readAllLines(userData, StandardCharsets.UTF_8));
            for (int i = 1; i < zeilen.size(); i++) {
                String[] t = zeilen.get(i).split(";");
                if (t[0].equalsIgnoreCase(username)) zeilen.set(i, t[0] + ";" + guthaben);
            }
            schreibeSicher(userData, zeilen);
        }

        /** FR-24: Runde in der Spielhistorie speichern. */
        void speichereRunde(String username, Karte spieler, Karte dealer, int zusatz, Rundenergebnis r)
                throws IOException {
            String zeile = String.join(";", LocalDateTime.now().format(ZEIT), username,
                    spieler.toString(), dealer.toString(), String.valueOf(BLIND),
                    String.valueOf(zusatz), r.ergebnis(), String.valueOf(r.betrag()));
            Files.writeString(gameHistory, zeile + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }

        /** FR-25: die letzten Runden eines Spielers. */
        List<String[]> ladeHistorie(String username, int anzahl) throws IOException {
            List<String[]> runden = new ArrayList<>();
            List<String> zeilen = Files.readAllLines(gameHistory, StandardCharsets.UTF_8);
            for (int i = zeilen.size() - 1; i >= 1 && runden.size() < anzahl; i--) {
                String[] t = zeilen.get(i).split(";");
                if (t[1].equalsIgnoreCase(username)) runden.add(t);
            }
            return runden;
        }

        /** Erst in eine Temp-Datei schreiben, dann ersetzen – so bleibt die Datei bei Fehlern intakt. */
        private void schreibeSicher(Path ziel, List<String> zeilen) throws IOException {
            Path tmp = ziel.resolveSibling(ziel.getFileName() + ".tmp");
            Files.write(tmp, zeilen, StandardCharsets.UTF_8);
            Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    // ------------------------------------------------------------------
    // Konsolen-Spiel
    // ------------------------------------------------------------------
    private final Datenbank db;
    private final Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
    private String username;
    private int guthaben;

    Kartenduell(Datenbank db) { this.db = db; }

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length > 0 && args[0].equals("--test")) { runTests(); return; }
        new Kartenduell(new Datenbank(Paths.get("daten"))).start();
    }

    void start() throws IOException {
        System.out.println("=== ASS IM ÄRMEL ===");
        anmelden();
        while (true) {
            System.out.println("\nGuthaben: " + guthaben + " €");
            System.out.println("[1] Runde spielen   [2] Einzahlen   [3] Auszahlen   [4] Spielhistorie   [0] Beenden");
            switch (leseZeile("Auswahl: ")) {
                case "1" -> spieleRunde();
                case "2" -> einzahlen();
                case "3" -> auszahlen();
                case "4" -> zeigeHistorie();
                case "0" -> { System.out.println("Bis zum nächsten Mal, " + username + "!"); return; }
                default  -> System.out.println("Ungültige Auswahl. Bitte 0 bis 4 eingeben.");
            }
        }
    }

    /** Function 1: Spieler anmelden (FR-01 bis FR-04). */
    void anmelden() throws IOException {
        while (true) {
            String name = leseZeile("Benutzername: ").trim();
            if (!name.matches("[A-Za-z0-9_-]{3,20}")) {
                System.out.println("Der Benutzername braucht 3–20 Zeichen (Buchstaben, Ziffern, _ oder -).");
                continue;
            }
            Integer gespeichert = db.ladeGuthaben(name);
            if (gespeichert == null) {
                db.neuerSpieler(name);
                guthaben = STARTGUTHABEN;
                System.out.println("Neuer Spieler angelegt. Startguthaben: " + STARTGUTHABEN + " €");
            } else {
                guthaben = gespeichert;
                System.out.println("Willkommen zurück, " + name + "!");
            }
            username = name;
            return;
        }
    }

    /** Function 2–7: eine komplette Spielrunde. */
    void spieleRunde() throws IOException {
        // Function 2: Blind setzen (FR-05 bis FR-07, NFR-03)
        if (guthaben < BLIND) {
            System.out.println("Für die Blind brauchst du mindestens " + BLIND + " €. Bitte zuerst einzahlen.");
            return;
        }
        System.out.println("\nBlind von " + BLIND + " € gesetzt. Guthaben: " + (guthaben - BLIND) + " €");

        // Function 3: Karten ziehen, nur Spieler-Karte zeigen (FR-08, FR-09)
        Karte[] karten = zieheZweiKarten();
        Karte spieler = karten[0], dealer = karten[1];
        System.out.println("Deine Karte:  " + spieler + " (Wert " + spieler.wert() + ")");
        System.out.println("Dealer-Karte: [verdeckt]");

        // Function 4: Zusatzeinsatz mit Chips (FR-10, FR-11, NFR-02)
        int zusatz = waehleZusatzeinsatz();

        // Function 5–7: berechnen
        int zufallszahl = ZUFALL.nextInt(100) + 1;   // 1–100
        Rundenergebnis r = berechneRunde(guthaben, spieler.wert(), dealer.wert(), zusatz, zufallszahl);

        if (zusatz > 0) {
            System.out.println("\n" + (r.dealerGehtMit() ? "Der Dealer geht mit." : "Der Dealer steigt aus."));
        } else {
            System.out.println("\nKein Zusatzeinsatz – der Dealer geht automatisch mit.");
        }
        System.out.println("Dealer-Karte: " + dealer + " (Wert " + dealer.wert() + ")");

        // Speichern (FR-24, NFR-05): bei Fehler bleibt das alte Guthaben
        try {
            db.speichereGuthaben(username, r.neuesGuthaben());
            db.speichereRunde(username, spieler, dealer, zusatz, r);
            guthaben = r.neuesGuthaben();
        } catch (IOException e) {
            System.out.println("Fehler beim Speichern. Dein Guthaben bleibt bei " + guthaben + " €.");
            return;
        }

        // Ergebnis anzeigen (NFR-07)
        System.out.println("\n>>> " + r.ergebnis() + " <<<");
        switch (r.ergebnis()) {
            case "FOLD" -> System.out.println("Der Dealer steigt aus. Du bekommst " + r.gesamteinsatz()
                    + " € zurück und gewinnst die Blind: +" + BLIND + " €");
            case "WIN"  -> System.out.println("Auszahlung " + r.auszahlung() + " € – du gewinnst +" + r.betrag() + " €");
            case "LOSE" -> System.out.println("Du verlierst deinen Einsatz von " + r.gesamteinsatz() + " €");
            case "DRAW" -> System.out.println("Gleichstand – dein Einsatz von " + r.gesamteinsatz() + " € wird zurückerstattet");
        }
        System.out.println("Neues Guthaben: " + guthaben + " €");
    }

    /** Spieler legt Chips nach, bis er fertig ist. */
    int waehleZusatzeinsatz() {
        int zusatz = 0;
        int maximal = guthaben - BLIND;
        System.out.println("\nZusatzeinsatz wählen: Chips 1, 5, 10, 20, 50 eingeben, 0 = fertig");
        while (true) {
            String eingabe = leseZeile("Einsatz " + (BLIND + zusatz) + " € | Guthaben " + (guthaben - BLIND - zusatz) + " € | Chip: ").trim();
            int chip;
            try { chip = Integer.parseInt(eingabe); }
            catch (NumberFormatException e) { System.out.println("Bitte eine Zahl eingeben."); continue; }
            if (chip == 0) return zusatz;
            if (Arrays.stream(CHIPS).noneMatch(c -> c == chip)) {
                System.out.println("Es gibt nur Chips mit 1, 5, 10, 20 und 50 €.");
            } else if (zusatz + chip > maximal) {
                System.out.println("Zu viel: Blind und Zusatzeinsatz dürfen dein Guthaben von " + guthaben + " € nicht übersteigen.");
            } else {
                zusatz += chip;
            }
        }
    }

    /** Function 8: Einzahlung (FR-26). */
    void einzahlen() throws IOException {
        int betrag = leseBetrag("Betrag einzahlen (€): ");
        if (betrag <= 0) return;
        db.speichereGuthaben(username, guthaben + betrag);
        guthaben += betrag;
        System.out.println(betrag + " € eingezahlt.");
    }

    /** Function 8: Auszahlung (FR-27, NFR-09). */
    void auszahlen() throws IOException {
        int betrag = leseBetrag("Betrag auszahlen (€): ");
        if (betrag <= 0) return;
        if (betrag > guthaben) {
            System.out.println("Du kannst höchstens dein Guthaben von " + guthaben + " € auszahlen.");
            return;
        }
        db.speichereGuthaben(username, guthaben - betrag);
        guthaben -= betrag;
        System.out.println(betrag + " € ausgezahlt.");
    }

    /** FR-25: Spielhistorie anzeigen. */
    void zeigeHistorie() throws IOException {
        List<String[]> runden = db.ladeHistorie(username, 10);
        if (runden.isEmpty()) { System.out.println("Noch keine Runden gespielt."); return; }
        System.out.println("\nDeine letzten Runden:");
        for (String[] t : runden) {
            int betrag = Integer.parseInt(t[7]);
            int einsatz = Integer.parseInt(t[4]) + Integer.parseInt(t[5]);
            System.out.printf("%s  %-4s  %s gegen %s  Einsatz %d €  %s%d €%n",
                    t[0], t[6], t[2], t[3], einsatz, betrag > 0 ? "+" : "", betrag);
        }
    }

    int leseBetrag(String text) {
        try {
            int b = Integer.parseInt(leseZeile(text).trim());
            if (b < 1) { System.out.println("Bitte einen Betrag ab 1 € eingeben."); return 0; }
            return b;
        } catch (NumberFormatException e) {
            System.out.println("Bitte eine ganze Zahl eingeben.");
            return 0;
        }
    }

    String leseZeile(String text) {
        System.out.print(text);
        if (!in.hasNextLine()) System.exit(0);
        return in.nextLine();
    }

    // ------------------------------------------------------------------
    // Tests mit festen Werten (java Kartenduell.java --test)
    // ------------------------------------------------------------------
    static void runTests() {
        // {Guthaben, Spieler, Dealer, Zusatz, Zufallszahl, erwartetes neues Guthaben}
        Object[][] faelle = {
            {100, 13, 8, 35, 41, "WIN", 145},
            {100, 5, 14, 50, 99, "LOSE", 40},
            {100, 9, 2, 10, 1, "FOLD", 110},
            {100, 7, 7, 0, 50, "DRAW", 100},
            {300, 12, 3, 200, 3, "FOLD", 310},
        };
        int ok = 0;
        for (int i = 0; i < faelle.length; i++) {
            Object[] f = faelle[i];
            Rundenergebnis r = berechneRunde((int) f[0], (int) f[1], (int) f[2], (int) f[3], (int) f[4]);
            boolean pass = r.ergebnis().equals(f[5]) && r.neuesGuthaben() == (int) f[6];
            if (pass) ok++;
            System.out.printf("Test %d: Chance %3d %%, %-4s, neues Guthaben %3d €  (erwartet %-4s %3d €)  %s%n",
                    i + 1, r.wahrscheinlichkeit(), r.ergebnis(), r.neuesGuthaben(), f[5], f[6], pass ? "OK" : "FEHLER");
        }
        System.out.println(ok + " von " + faelle.length + " Tests bestanden.");
    }
}
