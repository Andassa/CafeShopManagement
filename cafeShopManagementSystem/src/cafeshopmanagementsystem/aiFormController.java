package cafeshopmanagementsystem;

import java.net.URL;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class aiFormController implements Initializable {

    private static final Locale FR = Locale.FRANCE;

    @FXML
    private TableView<analyseVentes.Reappro> ai_stockTable;
    @FXML
    private TableColumn<analyseVentes.Reappro, String> ai_col_produit;
    @FXML
    private TableColumn<analyseVentes.Reappro, String> ai_col_stock;
    @FXML
    private TableColumn<analyseVentes.Reappro, String> ai_col_ventes;
    @FXML
    private TableColumn<analyseVentes.Reappro, String> ai_col_rupture;
    @FXML
    private TableColumn<analyseVentes.Reappro, String> ai_col_commande;
    @FXML
    private ListView<String> ai_alertList;
    @FXML
    private Label ai_status;
    @FXML
    private TextField ai_question;
    @FXML
    private Button ai_askBtn;
    @FXML
    private TextArea ai_answer;
    @FXML
    private TableView<List<String>> ai_resultTable;
    @FXML
    private Label ai_sql;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ai_col_produit.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().produit));
        ai_col_stock.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().stock)));
        ai_col_ventes.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.format(FR, "%.1f", c.getValue().ventesParJour)));
        ai_col_rupture.setCellValueFactory(c -> new ReadOnlyStringWrapper(libelleRupture(c.getValue().joursAvantRupture)));
        ai_col_commande.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().aCommander > 0 ? String.valueOf(c.getValue().aCommander) : "-"));
        ai_stockTable.setRowFactory(t -> new TableRow<analyseVentes.Reappro>() {
            @Override
            protected void updateItem(analyseVentes.Reappro item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("row-alerte");
                if (!empty && item != null && item.joursAvantRupture >= 0 && item.joursAvantRupture <= 3) {
                    getStyleClass().add("row-alerte");
                }
            }
        });

        ai_alertList.setCellFactory(l -> new ListCell<String>() {
            {
                setWrapText(true);
                prefWidthProperty().bind(ai_alertList.widthProperty().subtract(24));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });

        ai_status.setText(assistantIA.estConfigure()
                ? "Connecté : " + assistantIA.fournisseur()
                : "Inactif : ajoutez une clé gratuite dans AI_API_KEY (voir le README)");
        ai_answer.setText("Posez une question sur vos ventes, vos produits ou vos clients.\n\n"
                + "Exemples :\n"
                + "- Quel produit a rapporté le plus cette semaine ?\n"
                + "- Quel est le panier moyen par jour de la semaine ?\n"
                + "- Quels produits se vendent le mieux le samedi ?");
    }

    public void actualiser() {
        Task<Object[]> tache = new Task<Object[]>() {
            @Override
            protected Object[] call() throws Exception {
                try (Connection connect = database.connectDB()) {
                    analyseVentes.Donnees d = analyseVentes.charger(connect);
                    List<analyseVentes.Reappro> stock = new ArrayList<>();
                    for (Map.Entry<String, Integer> p : d.stockParProduit.entrySet()) {
                        stock.add(analyseVentes.reappro(p.getKey(), p.getValue(), d.ventesParProduit.get(p.getKey())));
                    }
                    stock.sort((a, b) -> Integer.compare(urgence(a), urgence(b)));
                    return new Object[]{stock, resume(d, stock)};
                }
            }
        };
        tache.setOnSucceeded(e -> {
            @SuppressWarnings("unchecked")
            List<analyseVentes.Reappro> stock = (List<analyseVentes.Reappro>) tache.getValue()[0];
            @SuppressWarnings("unchecked")
            List<String> alertes = (List<String>) tache.getValue()[1];
            ai_stockTable.setItems(FXCollections.observableArrayList(stock));
            ai_alertList.setItems(FXCollections.observableArrayList(alertes));
        });
        tache.setOnFailed(e -> ai_alertList.setItems(FXCollections.observableArrayList(
                "Analyse impossible : " + tache.getException().getMessage())));
        demarrer(tache);
    }

    @FXML
    public void demander() {
        String question = ai_question.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        if (!assistantIA.estConfigure()) {
            ai_answer.setText("L'assistant a besoin d'une clé d'API gratuite.\n\n"
                    + "1. Créez une clé sur https://console.groq.com/keys (gratuit).\n"
                    + "2. Avant de lancer l'application : export AI_API_KEY=votre_cle\n\n"
                    + "Autres options gratuites : Google Gemini ou Ollama en local (voir le README).");
            return;
        }

        ai_askBtn.setDisable(true);
        ai_answer.setText("Analyse en cours…");
        ai_sql.setText("");
        ai_resultTable.getColumns().clear();
        ai_resultTable.getItems().clear();

        Task<assistantIA.Reponse> tache = new Task<assistantIA.Reponse>() {
            @Override
            protected assistantIA.Reponse call() throws Exception {
                try (Connection connect = database.connectDB()) {
                    return assistantIA.demander(question, connect);
                }
            }
        };
        tache.setOnSucceeded(e -> {
            afficher(tache.getValue());
            ai_askBtn.setDisable(false);
        });
        tache.setOnFailed(e -> {
            ai_answer.setText("Désolé, je n'ai pas pu répondre.\n\n" + tache.getException().getMessage());
            ai_askBtn.setDisable(false);
        });
        demarrer(tache);
    }

    private void afficher(assistantIA.Reponse reponse) {
        ai_answer.setText(reponse.texte);
        ai_sql.setText(reponse.sql == null ? "" : "SQL : " + reponse.sql.replaceAll("\\s+", " "));
        for (int i = 0; i < reponse.colonnes.size(); i++) {
            final int index = i;
            TableColumn<List<String>, String> colonne = new TableColumn<>(reponse.colonnes.get(i));
            colonne.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().get(index)));
            ai_resultTable.getColumns().add(colonne);
        }
        ai_resultTable.setItems(FXCollections.observableArrayList(reponse.lignes));
    }

    private static List<String> resume(analyseVentes.Donnees d, List<analyseVentes.Reappro> stock) {
        List<String> lignes = new ArrayList<>();
        int n = analyseVentes.HISTORIQUE_JOURS;
        LocalDate aujourdhui = d.debut.plusDays(n - 1);

        double reference = 0;
        int nbReferences = 0;
        for (int k = n - 1 - analyseVentes.SAISON; k >= 0 && nbReferences < 4; k -= analyseVentes.SAISON) {
            reference += d.revenus[k];
            nbReferences++;
        }
        if (nbReferences > 0 && reference > 0) {
            reference /= nbReferences;
            double ecart = (d.revenus[n - 1] - reference) / reference * 100;
            lignes.add(String.format(FR, "Aujourd'hui : %.2f $ encaissés pour l'instant, contre %.2f $ un %s habituel (%+.0f %%).",
                    d.revenus[n - 1], reference, jour(aujourdhui), ecart));
        }

        String meilleur = null;
        double meilleurVentes = 0;
        double meilleurAvant = 0;
        for (Map.Entry<String, double[]> p : d.ventesParProduit.entrySet()) {
            double semaine = analyseVentes.somme(p.getValue(), n - 7, n);
            if (semaine > meilleurVentes) {
                meilleur = p.getKey();
                meilleurVentes = semaine;
                meilleurAvant = analyseVentes.somme(p.getValue(), n - 14, n - 7);
            }
        }
        if (meilleur != null) {
            String tendance = meilleurAvant > 0
                    ? String.format(FR, " (%+.0f %% par rapport à la semaine précédente)", (meilleurVentes - meilleurAvant) / meilleurAvant * 100)
                    : "";
            lignes.add(String.format(FR, "Produit star des 7 derniers jours : %s, %.0f vendus%s.", meilleur, meilleurVentes, tendance));
        }

        for (analyseVentes.Reappro r : stock) {
            if (r.joursAvantRupture == 0) {
                lignes.add(String.format(FR, "Rupture : %s n'a plus assez de stock pour aujourd'hui, commander %d unités.", r.produit, r.aCommander));
            } else if (r.joursAvantRupture > 0 && r.joursAvantRupture <= 3) {
                lignes.add(String.format(FR, "Stock bas : %s sera épuisé %s, commander %d unités.",
                        r.produit, libelleRupture(r.joursAvantRupture).toLowerCase(FR), r.aCommander));
            }
        }

        for (analyseVentes.Anomalie a : analyseVentes.anomalies(d.revenus)) {
            LocalDate date = d.debut.plusDays(a.indexJour);
            lignes.add(String.format(FR, "Anomalie le %s %d/%02d : %.2f $ de revenus, %+.0f %% par rapport à un %s habituel (%.2f $).",
                    jour(date), date.getDayOfMonth(), date.getMonthValue(), a.valeur, a.ecartPourcent(), jour(date), a.reference));
        }

        for (String produit : analyseVentes.produitsEnBaisse(d.ventesParProduit)) {
            lignes.add(String.format(FR, "En perte de vitesse : %s ne s'est plus vendu depuis 7 jours alors qu'il se vendait avant.", produit));
        }

        if (lignes.isEmpty()) {
            lignes.add("Pas encore assez d'historique de ventes pour une analyse.");
        }
        return lignes;
    }

    private static int urgence(analyseVentes.Reappro r) {
        return r.joursAvantRupture < 0 ? Integer.MAX_VALUE : r.joursAvantRupture;
    }

    private static String libelleRupture(int jours) {
        if (jours < 0) {
            return "> " + analyseVentes.HORIZON_JOURS + " jours";
        }
        if (jours == 0) {
            return "Aujourd'hui";
        }
        return jours == 1 ? "Demain" : "Dans " + jours + " jours";
    }

    private static String jour(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, FR);
    }

    private static void demarrer(Task<?> tache) {
        Thread thread = new Thread(tache, "analyse-ia");
        thread.setDaemon(true);
        thread.start();
    }
}
