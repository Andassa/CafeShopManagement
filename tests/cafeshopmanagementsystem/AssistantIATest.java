package cafeshopmanagementsystem;

public class AssistantIATest {

    public static void main(String[] args) throws Exception {
        Verif v = new Verif("AssistantIA");

        v.egal("SELECT accepté et limité",
                "SELECT * FROM (SELECT prod_name FROM product) AS resultat LIMIT 200",
                assistantIA.verifierRequete("SELECT prod_name FROM product;"));
        v.vrai("WITH accepté", assistantIA.verifierRequete(
                "WITH v AS (SELECT prod_name, SUM(price) AS total FROM customer GROUP BY prod_name) SELECT * FROM v")
                .startsWith("SELECT * FROM (WITH"));

        refuse(v, "Suppression", "DELETE FROM product");
        refuse(v, "Requêtes multiples", "SELECT 1; DROP TABLE product");
        refuse(v, "Commentaire", "SELECT * FROM product -- x");
        refuse(v, "Commentaire bloc", "SELECT /* x */ * FROM product");
        refuse(v, "Table des employés (mots de passe)", "SELECT username, password FROM employee");
        refuse(v, "Export de fichier", "SELECT * FROM product INTO OUTFILE '/tmp/x'");
        refuse(v, "Attente (déni de service)", "SELECT SLEEP(10)");
        refuse(v, "Tables système", "SELECT * FROM mysql.user");
        refuse(v, "Schéma système", "SELECT * FROM information_schema.tables");
        refuse(v, "Mise à jour masquée", "UPDATE product SET stock = 0");
        refuse(v, "Vide", "  ;  ");

        v.egal("JSON extrait d'une réponse bavarde", "SELECT 1",
                assistantIA.extraireJson("Voici : {\"sql\": \"SELECT 1\", \"explication\": \"test\"} Merci").path("sql").asText());

        v.terminer();
    }

    private static void refuse(Verif v, String cas, String sql) {
        try {
            assistantIA.verifierRequete(sql);
            v.vrai("Refusé : " + cas, false);
        } catch (IllegalArgumentException attendu) {
            v.vrai("Refusé : " + cas, true);
        }
    }
}
