package cafeshopmanagementsystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalyseVentesTest {

    public static void main(String[] args) {
        Verif v = new Verif("AnalyseVentes");

        double[] motif = {8, 9, 10, 11, 14, 20, 18};
        double[] serie = new double[42];
        for (int i = 0; i < serie.length; i++) {
            serie[i] = motif[i % 7];
        }
        analyseVentes.Prevision p = analyseVentes.holtWinters(serie, 7);
        for (int h = 0; h < 7; h++) {
            v.proche("Holt-Winters retrouve la saisonnalité (jour " + h + ")", motif[h], p.valeurs[h], 1.5);
        }

        double[] constante = new double[42];
        Arrays.fill(constante, 10);
        analyseVentes.Reappro r = analyseVentes.reappro("Croissant", 25, constante);
        v.egal("Rupture estimée après 2 jours pleins", 2, r.joursAvantRupture);
        v.proche("Ventes moyennes prévues", 10, r.ventesParJour, 0.5);
        v.vrai("Quantité à commander couvre 7 jours", r.aCommander >= 45 && r.aCommander <= 50);

        v.egal("Stock nul : rupture immédiate", 0, analyseVentes.reappro("Muffin", 0, constante).joursAvantRupture);
        v.egal("Gros stock : pas de rupture sur l'horizon", -1, analyseVentes.reappro("Espresso", 10000, constante).joursAvantRupture);
        v.egal("Sans historique : rien à commander", 0, analyseVentes.reappro("Nouveau", 5, new double[0]).aCommander);

        List<Set<String>> paniers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            paniers.add(panier("Cappuccino", i < 7 ? "Croissant" : "Espresso"));
        }
        for (int i = 0; i < 10; i++) {
            paniers.add(panier("Latte"));
        }
        analyseVentes.Suggestion s = analyseVentes.suggerer(paniers, panier("Cappuccino"));
        v.vrai("Suggestion trouvée", s != null);
        v.egal("Suggère le produit le plus associé", "Croissant", s.produit);
        v.proche("Confiance de la règle", 0.7, s.confiance, 1e-9);
        v.vrai("Ne suggère pas un produit déjà au panier",
                analyseVentes.suggerer(paniers, panier("Cappuccino", "Croissant")) == null
                || !"Croissant".equals(analyseVentes.suggerer(paniers, panier("Cappuccino", "Croissant")).produit));
        v.vrai("Panier vide : aucune suggestion", analyseVentes.suggerer(paniers, new HashSet<String>()) == null);

        double[] revenus = new double[42];
        for (int i = 0; i < revenus.length; i++) {
            revenus[i] = 100 + 10 * (i % 7);
        }
        revenus[35] = 30;
        List<analyseVentes.Anomalie> anomalies = analyseVentes.anomalies(revenus);
        v.egal("Une seule anomalie détectée", 1, anomalies.size());
        v.egal("Anomalie sur le bon jour", 35, anomalies.get(0).indexJour);
        v.vrai("Écart négatif important", anomalies.get(0).ecartPourcent() < -50);

        Map<String, double[]> ventes = new LinkedHashMap<>();
        double[] arrete = new double[42];
        Arrays.fill(arrete, 0, 35, 2);
        ventes.put("Muffin", arrete);
        ventes.put("Espresso", constante);
        v.egal("Produit en perte de vitesse", Arrays.asList("Muffin"), analyseVentes.produitsEnBaisse(ventes));

        v.terminer();
    }

    private static Set<String> panier(String... produits) {
        return new HashSet<>(Arrays.asList(produits));
    }
}
