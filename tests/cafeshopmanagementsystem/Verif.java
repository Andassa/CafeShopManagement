package cafeshopmanagementsystem;

import java.util.Objects;

/** Mini-outil d'assertions pour lancer les tests sans dépendance externe. */
class Verif {

    private final String suite;
    private int succes;
    private int echecs;

    Verif(String suite) {
        this.suite = suite;
    }

    void vrai(String nom, boolean condition) {
        if (condition) {
            succes++;
        } else {
            echecs++;
            System.out.println("  ÉCHEC : " + nom);
        }
    }

    void egal(String nom, Object attendu, Object obtenu) {
        vrai(nom + " (attendu " + attendu + ", obtenu " + obtenu + ")", Objects.equals(attendu, obtenu));
    }

    void proche(String nom, double attendu, double obtenu, double tolerance) {
        vrai(nom + " (attendu " + attendu + ", obtenu " + obtenu + ")", Math.abs(attendu - obtenu) <= tolerance);
    }

    void terminer() {
        System.out.println(suite + " : " + succes + " réussis, " + echecs + " échoués");
        if (echecs > 0) {
            System.exit(1);
        }
    }
}
