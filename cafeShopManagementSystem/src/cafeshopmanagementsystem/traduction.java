package cafeshopmanagementsystem;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.control.TableCell;
import javafx.util.StringConverter;

/**
 * Libellés français des valeurs enregistrées en base.
 * Les valeurs stockées restent en anglais : elles sont comparées dans le code
 * et utilisées par les comptes et produits déjà existants.
 */
public class traduction {

    private static final Map<String, String> FR = new LinkedHashMap<>();

    static {
        FR.put("Meals", "Plats");
        FR.put("Drinks", "Boissons");
        FR.put("Available", "Disponible");
        FR.put("Unavailable", "Indisponible");
        FR.put("What is your favorite Color?", "Quelle est votre couleur préférée ?");
        FR.put("What is your favorite food?", "Quel est votre plat préféré ?");
        FR.put("what is your birth date?", "Quelle est votre date de naissance ?");
    }

    public static String fr(String valeur) {
        if (valeur == null) {
            return null;
        }
        String libelle = FR.get(valeur);
        return libelle != null ? libelle : valeur;
    }

    public static String valeur(String libelle) {
        for (Map.Entry<String, String> entree : FR.entrySet()) {
            if (entree.getValue().equals(libelle)) {
                return entree.getKey();
            }
        }
        return libelle;
    }

    public static StringConverter<String> convertisseur() {
        return new StringConverter<String>() {
            @Override
            public String toString(String valeur) {
                return fr(valeur);
            }

            @Override
            public String fromString(String libelle) {
                return valeur(libelle);
            }
        };
    }

    public static <S> TableCell<S, String> cellule() {
        return new TableCell<S, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : fr(item));
            }
        };
    }
}
