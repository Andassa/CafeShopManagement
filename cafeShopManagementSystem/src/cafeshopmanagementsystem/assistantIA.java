package cafeshopmanagementsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assistant en langage naturel : la question est traduite en SQL par un modèle de langage
 * compatible avec l'API OpenAI (Groq, Google Gemini, Ollama...), puis exécutée en lecture seule.
 */
public class assistantIA {

    public static final String API_KEY = env("AI_API_KEY", "");
    public static final String BASE_URL = env("AI_BASE_URL", "https://api.groq.com/openai/v1").replaceAll("/+$", "");
    public static final String MODEL = env("AI_MODEL", "llama-3.3-70b-versatile");

    private static final int LIGNES_MAX = 200;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern INTERDIT = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|replace|grant|revoke|load_file|load|outfile|dumpfile"
            + "|sleep|benchmark|set|call|handler|lock|unlock|rename|into|prepare|execute|employee"
            + "|information_schema|performance_schema|mysql|sys)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String PROMPT_SQL = String.join("\n",
            "Tu es l'assistant d'analyse d'un café. Tu traduis la question de l'utilisateur en UNE requête MySQL 8 de lecture.",
            "Schéma :",
            "- product(id, prod_id, prod_name, type, stock, price, status, image, date) : catalogue.",
            "  type vaut 'Meals' (plats) ou 'Drinks' (boissons) ; status vaut 'Available' ou 'Unavailable'.",
            "- customer(id, customer_id, prod_id, prod_name, type, quantity, price, date, image, em_username) :",
            "  une ligne par produit commandé ; customer_id identifie la commande ; price = prix total de la ligne.",
            "- receipt(id, customer_id, total, date, em_username) : une ligne par commande payée, total en dollars.",
            "Règles : uniquement SELECT (ou WITH), jamais la table employee, pas de commentaire, pas de point-virgule.",
            "Pour les ventes, ne garde que les commandes payées : customer_id IN (SELECT customer_id FROM receipt).",
            "Utilise CURDATE() pour aujourd'hui. Donne des alias de colonnes courts en français.",
            "Réponds uniquement avec un objet JSON : {\"sql\": \"...\", \"explication\": \"une phrase en français\"}.",
            "Si la question ne concerne pas les données du café, réponds {\"sql\": null, \"explication\": \"...\"}.");

    private static final String PROMPT_REPONSE = String.join("\n",
            "Tu es l'assistant d'analyse d'un café. À partir de la question et du résultat SQL fourni,",
            "réponds en français en 2 à 4 phrases claires, avec les chiffres importants (montants en $).",
            "N'invente aucune donnée absente du résultat. Pas de markdown.");

    public static class Reponse {
        public String texte;
        public String sql;
        public final List<String> colonnes = new ArrayList<>();
        public final List<List<String>> lignes = new ArrayList<>();
    }

    public static boolean estConfigure() {
        return !API_KEY.isEmpty() || BASE_URL.contains("localhost") || BASE_URL.contains("127.0.0.1");
    }

    public static String fournisseur() {
        String hote = BASE_URL.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        return MODEL + " via " + hote;
    }

    public static Reponse demander(String question, Connection connect) throws Exception {
        Reponse reponse = new Reponse();

        JsonNode plan = extraireJson(discuter(PROMPT_SQL, question));
        JsonNode sql = plan.path("sql");
        if (sql.isMissingNode() || sql.isNull() || sql.asText().trim().isEmpty()) {
            reponse.texte = texte(plan.path("explication"), "Je ne peux répondre qu'aux questions sur les données du café.");
            return reponse;
        }

        reponse.sql = sql.asText().trim();
        executer(verifierRequete(reponse.sql), connect, reponse);

        ObjectNode contexte = MAPPER.createObjectNode();
        contexte.put("question", question);
        contexte.put("sql", reponse.sql);
        ArrayNode lignes = contexte.putArray("resultat");
        for (List<String> ligne : reponse.lignes.subList(0, Math.min(50, reponse.lignes.size()))) {
            ObjectNode objet = lignes.addObject();
            for (int i = 0; i < reponse.colonnes.size(); i++) {
                objet.put(reponse.colonnes.get(i), ligne.get(i));
            }
        }
        reponse.texte = discuter(PROMPT_REPONSE, MAPPER.writeValueAsString(contexte)).trim();
        return reponse;
    }

    /**
     * Garde-fou : n'accepte qu'une seule requête de lecture, sans commentaire ni mot-clé
     * dangereux, et limite le nombre de lignes renvoyées.
     */
    public static String verifierRequete(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("Requête vide.");
        }
        String s = sql.trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Requête vide.");
        }
        if (s.contains(";")) {
            throw new IllegalArgumentException("Une seule requête est autorisée.");
        }
        if (s.contains("--") || s.contains("/*") || s.contains("#")) {
            throw new IllegalArgumentException("Les commentaires SQL ne sont pas autorisés.");
        }
        String minuscule = s.toLowerCase(Locale.ROOT);
        if (!minuscule.startsWith("select") && !minuscule.startsWith("with")) {
            throw new IllegalArgumentException("Seules les requêtes de lecture (SELECT) sont autorisées.");
        }
        Matcher m = INTERDIT.matcher(s);
        if (m.find()) {
            throw new IllegalArgumentException("Mot-clé interdit dans la requête : " + m.group().toUpperCase(Locale.ROOT));
        }
        return "SELECT * FROM (" + s + ") AS resultat LIMIT " + LIGNES_MAX;
    }

    public static JsonNode extraireJson(String texte) throws IOException {
        int debut = texte.indexOf('{');
        int fin = texte.lastIndexOf('}');
        if (debut < 0 || fin <= debut) {
            throw new IOException("Réponse du modèle illisible : " + texte);
        }
        return MAPPER.readTree(texte.substring(debut, fin + 1));
    }

    private static void executer(String sql, Connection connect, Reponse reponse) throws Exception {
        connect.setReadOnly(true);
        try (Statement st = connect.createStatement()) {
            st.setQueryTimeout(5);
            st.setMaxRows(LIGNES_MAX);
            try (ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    reponse.colonnes.add(meta.getColumnLabel(i));
                }
                while (rs.next()) {
                    List<String> ligne = new ArrayList<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        ligne.add(rs.getString(i));
                    }
                    reponse.lignes.add(ligne);
                }
            }
        }
    }

    private static String discuter(String systeme, String utilisateur) throws IOException {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", MODEL);
        corps.put("temperature", 0);
        ArrayNode messages = corps.putArray("messages");
        messages.addObject().put("role", "system").put("content", systeme);
        messages.addObject().put("role", "user").put("content", utilisateur);

        HttpURLConnection con = (HttpURLConnection) new URL(BASE_URL + "/chat/completions").openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(10000);
        con.setReadTimeout(60000);
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!API_KEY.isEmpty()) {
            con.setRequestProperty("Authorization", "Bearer " + API_KEY);
        }
        try (OutputStream out = con.getOutputStream()) {
            out.write(MAPPER.writeValueAsBytes(corps));
        }

        int code = con.getResponseCode();
        String texte = lire(code < 400 ? con.getInputStream() : con.getErrorStream());
        if (code >= 400) {
            String detail = texte;
            try {
                detail = texte(MAPPER.readTree(texte).path("error").path("message"), texte);
            } catch (IOException ignore) {
                // corps d'erreur non JSON : on garde le texte brut
            }
            throw new IOException("Service IA indisponible (HTTP " + code + ") : " + detail);
        }
        return MAPPER.readTree(texte).path("choices").path(0).path("message").path("content").asText();
    }

    private static String lire(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream flux = in) {
            ByteArrayOutputStream tampon = new ByteArrayOutputStream();
            byte[] bloc = new byte[8192];
            int n;
            while ((n = flux.read(bloc)) != -1) {
                tampon.write(bloc, 0, n);
            }
            return new String(tampon.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String texte(JsonNode noeud, String defaut) {
        return (noeud.isMissingNode() || noeud.isNull() || noeud.asText().isEmpty()) ? defaut : noeud.asText();
    }

    private static String env(String cle, String defaut) {
        String valeur = System.getenv(cle);
        return (valeur == null || valeur.trim().isEmpty()) ? defaut : valeur.trim();
    }
}
