package cafeshopmanagementsystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyses des ventes calculées localement (aucun service externe) :
 * prévision de stock, suggestions de produits et détection d'anomalies.
 */
public class analyseVentes {

    public static final int HISTORIQUE_JOURS = 42;
    public static final int SAISON = 7;
    public static final int COUVERTURE_JOURS = 7;
    public static final int HORIZON_JOURS = 30;

    private static final double ALPHA = 0.3;
    private static final double BETA = 0.05;
    private static final double GAMMA = 0.3;
    private static final double Z_SERVICE_95 = 1.65;

    // ---------------------------------------------------------------- Prévision

    public static class Prevision {
        public final double[] valeurs;
        public final double ecartType;

        Prevision(double[] valeurs, double ecartType) {
            this.valeurs = valeurs;
            this.ecartType = ecartType;
        }
    }

    /** Lissage de Holt-Winters additif (saisonnalité hebdomadaire). */
    public static Prevision holtWinters(double[] serie, int horizon) {
        int n = serie.length;
        double[] prevision = new double[horizon];
        if (n == 0) {
            return new Prevision(prevision, 0);
        }
        if (n < 2 * SAISON) {
            double m = moyenne(serie, 0, n);
            Arrays.fill(prevision, m);
            return new Prevision(prevision, ecartType(serie, m));
        }

        double niveau = moyenne(serie, 0, SAISON);
        double tendance = (moyenne(serie, SAISON, 2 * SAISON) - niveau) / SAISON;
        double[] saison = new double[SAISON];
        for (int i = 0; i < SAISON; i++) {
            saison[i] = serie[i] - niveau;
        }

        double sommeErreurs2 = 0;
        int nbErreurs = 0;
        for (int t = SAISON; t < n; t++) {
            int s = t % SAISON;
            double erreur = serie[t] - (niveau + tendance + saison[s]);
            sommeErreurs2 += erreur * erreur;
            nbErreurs++;

            double ancienNiveau = niveau;
            niveau = ALPHA * (serie[t] - saison[s]) + (1 - ALPHA) * (niveau + tendance);
            tendance = BETA * (niveau - ancienNiveau) + (1 - BETA) * tendance;
            saison[s] = GAMMA * (serie[t] - niveau) + (1 - GAMMA) * saison[s];
        }

        for (int h = 1; h <= horizon; h++) {
            prevision[h - 1] = Math.max(0, niveau + h * tendance + saison[(n + h - 1) % SAISON]);
        }
        return new Prevision(prevision, Math.sqrt(sommeErreurs2 / Math.max(1, nbErreurs)));
    }

    public static class Reappro {
        public final String produit;
        public final int stock;
        public final double ventesParJour;
        /** Nombre de jours couverts par le stock, -1 si au-delà de l'horizon. */
        public final int joursAvantRupture;
        public final int aCommander;

        Reappro(String produit, int stock, double ventesParJour, int joursAvantRupture, int aCommander) {
            this.produit = produit;
            this.stock = stock;
            this.ventesParJour = ventesParJour;
            this.joursAvantRupture = joursAvantRupture;
            this.aCommander = aCommander;
        }
    }

    public static Reappro reappro(String produit, int stock, double[] ventesJournalieres) {
        Prevision p = holtWinters(ventesJournalieres, HORIZON_JOURS);

        int rupture = -1;
        double cumul = 0;
        for (int h = 0; h < HORIZON_JOURS; h++) {
            cumul += p.valeurs[h];
            if (stock <= 0 || cumul > stock) {
                rupture = stock <= 0 ? 0 : h;
                break;
            }
        }

        double besoin = 0;
        for (int h = 0; h < COUVERTURE_JOURS; h++) {
            besoin += p.valeurs[h];
        }
        double stockSecurite = Z_SERVICE_95 * p.ecartType * Math.sqrt(COUVERTURE_JOURS);
        int aCommander = (int) Math.max(0, Math.ceil(besoin + stockSecurite - stock));

        return new Reappro(produit, stock, besoin / COUVERTURE_JOURS, rupture, aCommander);
    }

    // ---------------------------------------------------------------- Suggestions

    public static class Suggestion {
        public final String produit;
        public final String declencheur;
        public final double confiance;
        public final double lift;

        Suggestion(String produit, String declencheur, double confiance, double lift) {
            this.produit = produit;
            this.declencheur = declencheur;
            this.confiance = confiance;
            this.lift = lift;
        }
    }

    /** Règle d'association A -> B la plus fiable pour le panier en cours. */
    public static Suggestion suggerer(Collection<Set<String>> paniers, Set<String> panierActuel) {
        if (paniers.isEmpty() || panierActuel.isEmpty()) {
            return null;
        }
        Map<String, Integer> frequence = new HashMap<>();
        Map<String, Map<String, Integer>> paires = new HashMap<>();
        for (Set<String> panier : paniers) {
            for (String a : panier) {
                frequence.merge(a, 1, Integer::sum);
                for (String b : panier) {
                    if (!a.equals(b)) {
                        paires.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
                    }
                }
            }
        }

        int total = paniers.size();
        Suggestion meilleure = null;
        for (String a : panierActuel) {
            int freqA = frequence.getOrDefault(a, 0);
            if (freqA < 3 || !paires.containsKey(a)) {
                continue;
            }
            for (Map.Entry<String, Integer> paire : paires.get(a).entrySet()) {
                String b = paire.getKey();
                if (panierActuel.contains(b) || paire.getValue() < 2) {
                    continue;
                }
                double confiance = (double) paire.getValue() / freqA;
                double lift = confiance / ((double) frequence.get(b) / total);
                if (lift < 1.1) {
                    continue;
                }
                if (meilleure == null || confiance > meilleure.confiance
                        || (confiance == meilleure.confiance && lift > meilleure.lift)) {
                    meilleure = new Suggestion(b, a, confiance, lift);
                }
            }
        }
        return meilleure;
    }

    // ---------------------------------------------------------------- Anomalies

    public static class Anomalie {
        public final int indexJour;
        public final double valeur;
        public final double reference;

        Anomalie(int indexJour, double valeur, double reference) {
            this.indexJour = indexJour;
            this.valeur = valeur;
            this.reference = reference;
        }

        public double ecartPourcent() {
            return reference == 0 ? 0 : (valeur - reference) / reference * 100;
        }
    }

    /**
     * Compare chaque jour des deux dernières semaines aux mêmes jours de la semaine
     * précédents (médiane et écart absolu médian, robustes aux valeurs extrêmes).
     * Le dernier jour (aujourd'hui, incomplet) est ignoré.
     */
    public static List<Anomalie> anomalies(double[] revenusJournaliers) {
        List<Anomalie> resultat = new ArrayList<>();
        int n = revenusJournaliers.length;
        for (int t = Math.max(0, n - 15); t < n - 1; t++) {
            List<Double> references = new ArrayList<>();
            for (int k = t - SAISON; k >= 0 && references.size() < 4; k -= SAISON) {
                references.add(revenusJournaliers[k]);
            }
            if (references.size() < 2) {
                continue;
            }
            double m = mediane(references);
            List<Double> ecarts = new ArrayList<>();
            for (double r : references) {
                ecarts.add(Math.abs(r - m));
            }
            double echelle = Math.max(Math.max(1.4826 * mediane(ecarts), 0.15 * m), 1.0);
            double z = (revenusJournaliers[t] - m) / echelle;
            if (m > 0 && Math.abs(z) >= 3) {
                resultat.add(new Anomalie(t, revenusJournaliers[t], m));
            }
        }
        return resultat;
    }

    /** Produits vendus régulièrement auparavant mais plus du tout sur les 7 derniers jours. */
    public static List<String> produitsEnBaisse(Map<String, double[]> ventesParProduit) {
        List<String> resultat = new ArrayList<>();
        for (Map.Entry<String, double[]> e : ventesParProduit.entrySet()) {
            double[] v = e.getValue();
            int n = v.length;
            if (n < 28) {
                continue;
            }
            double avant = somme(v, n - 28, n - 7);
            double recent = somme(v, n - 7, n);
            if (avant >= 5 && recent == 0) {
                resultat.add(e.getKey());
            }
        }
        return resultat;
    }

    // ---------------------------------------------------------------- Accès aux données

    public static class Donnees {
        public final LocalDate debut;
        public final Map<String, double[]> ventesParProduit = new LinkedHashMap<>();
        public final Map<String, Integer> stockParProduit = new LinkedHashMap<>();
        public final double[] revenus = new double[HISTORIQUE_JOURS];

        Donnees(LocalDate debut) {
            this.debut = debut;
        }
    }

    public static Donnees charger(Connection connect) throws Exception {
        LocalDate aujourdhui = LocalDate.now();
        Donnees d = new Donnees(aujourdhui.minusDays(HISTORIQUE_JOURS - 1));

        try (PreparedStatement ps = connect.prepareStatement("SELECT prod_name, stock FROM product ORDER BY prod_name");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                d.stockParProduit.put(rs.getString(1), rs.getInt(2));
                d.ventesParProduit.put(rs.getString(1), new double[HISTORIQUE_JOURS]);
            }
        }

        String ventes = "SELECT prod_name, date, SUM(quantity) FROM customer "
                + "WHERE date >= ? AND customer_id IN (SELECT customer_id FROM receipt) "
                + "GROUP BY prod_name, date";
        try (PreparedStatement ps = connect.prepareStatement(ventes)) {
            ps.setString(1, d.debut.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double[] serie = d.ventesParProduit.get(rs.getString(1));
                    int i = index(d.debut, rs.getDate(2).toLocalDate());
                    if (serie != null && i >= 0) {
                        serie[i] += rs.getDouble(3);
                    }
                }
            }
        }

        try (PreparedStatement ps = connect.prepareStatement("SELECT date, SUM(total) FROM receipt WHERE date >= ? GROUP BY date")) {
            ps.setString(1, d.debut.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int i = index(d.debut, rs.getDate(1).toLocalDate());
                    if (i >= 0) {
                        d.revenus[i] += rs.getDouble(2);
                    }
                }
            }
        }
        return d;
    }

    public static List<Set<String>> chargerPaniers(Connection connect) throws Exception {
        Map<Integer, Set<String>> paniers = new HashMap<>();
        String sql = "SELECT customer_id, prod_name FROM customer WHERE customer_id IN (SELECT customer_id FROM receipt)";
        try (PreparedStatement ps = connect.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                paniers.computeIfAbsent(rs.getInt(1), k -> new HashSet<>()).add(rs.getString(2));
            }
        }
        return new ArrayList<>(paniers.values());
    }

    // ---------------------------------------------------------------- Outils

    private static int index(LocalDate debut, LocalDate date) {
        long i = date.toEpochDay() - debut.toEpochDay();
        return (i >= 0 && i < HISTORIQUE_JOURS) ? (int) i : -1;
    }

    static double somme(double[] v, int debut, int fin) {
        double s = 0;
        for (int i = Math.max(0, debut); i < Math.min(v.length, fin); i++) {
            s += v[i];
        }
        return s;
    }

    static double moyenne(double[] v, int debut, int fin) {
        return fin > debut ? somme(v, debut, fin) / (fin - debut) : 0;
    }

    private static double ecartType(double[] v, double m) {
        double s = 0;
        for (double x : v) {
            s += (x - m) * (x - m);
        }
        return v.length > 1 ? Math.sqrt(s / (v.length - 1)) : 0;
    }

    private static double mediane(List<Double> valeurs) {
        List<Double> tri = new ArrayList<>(valeurs);
        tri.sort(null);
        int n = tri.size();
        return n % 2 == 1 ? tri.get(n / 2) : (tri.get(n / 2 - 1) + tri.get(n / 2)) / 2;
    }
}
