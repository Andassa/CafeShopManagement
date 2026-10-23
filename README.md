# ☕ CafeShopManagement

Application de bureau de **gestion de café** développée en **JavaFX** avec une base de données **MySQL**.
Elle permet de gérer les employés, le stock de produits, la prise de commandes, les paiements et l'édition de reçus.

![Java](https://img.shields.io/badge/Java-8-orange?logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-UI-blue)
![MySQL](https://img.shields.io/badge/MySQL-Database-4479A1?logo=mysql&logoColor=white)
![JasperReports](https://img.shields.io/badge/JasperReports-Reçus-green)
![NetBeans](https://img.shields.io/badge/IDE-NetBeans-1B6AC6?logo=apachenetbeanside)

---

## 📑 Sommaire

- [Fonctionnalités](#-fonctionnalités)
- [Technologies](#-technologies)
- [Structure du projet](#-structure-du-projet)
- [Base de données](#-base-de-données)
- [Installation et lancement](#-installation-et-lancement)
- [Auteur](#-auteur)

---

## ✨ Fonctionnalités

### 🔐 Authentification
- Connexion des employés (nom d'utilisateur / mot de passe)
- Création de compte avec **question de sécurité**
- Réinitialisation du mot de passe via la question de sécurité

### 📊 Tableau de bord
- Nombre total de clients
- Revenu du jour et revenu total
- Nombre de produits vendus
- Graphiques d'évolution des **revenus** et des **clients** par date

### 📦 Inventaire
- Ajout, modification, suppression de produits
- Catégories : `Meals` / `Drinks`
- Statut : `Available` / `Unavailable`
- Gestion du stock et import d'une image par produit

### 🧾 Menu & commandes
- Affichage des produits sous forme de **cartes**
- Ajout au panier avec choix de la quantité (le stock est décrémenté automatiquement)
- Calcul du total, saisie du montant payé et rendu de monnaie
- Paiement et génération d'un **reçu** imprimable (JasperReports)

### 👥 Clients
- Historique des commandes / reçus (ID client, total, date, caissier)

---

## 🛠 Technologies

| Composant        | Outil                        |
|------------------|------------------------------|
| Langage          | Java 8                       |
| Interface        | JavaFX (FXML + CSS)          |
| Base de données  | MySQL (`mysql-connector-java 5.1.48`) |
| Reçus            | JasperReports 6.3.0          |
| Icônes           | FontAwesomeFX 8.2            |
| IDE / Build      | NetBeans + Ant               |

---

## 📁 Structure du projet

```
cafeShopManagementSystem/
├── src/cafeshopmanagementsystem/
│   ├── CafeShopManagementSystem.java   # Point d'entrée de l'application
│   ├── database.java                   # Connexion MySQL
│   ├── FXMLDocument.fxml / Controller  # Connexion, inscription, mot de passe oublié
│   ├── mainForm.fxml / Controller      # Dashboard, inventaire, menu, clients
│   ├── cardProduct.fxml / Controller   # Carte produit du menu
│   ├── productData.java                # Modèle produit
│   ├── customersData.java              # Modèle client / reçu
│   ├── data.java                       # Données partagées (session)
│   ├── report.jrxml                    # Modèle du reçu JasperReports
│   └── *.css                           # Styles des écrans
├── dist/lib/                           # Dépendances (.jar)
├── nbproject/                          # Configuration NetBeans
└── build.xml                           # Script Ant
```

---

## 🗄 Base de données

Nom de la base : **`cafe`**

```sql
CREATE DATABASE cafe;
USE cafe;

CREATE TABLE employee (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    username  VARCHAR(100) NOT NULL UNIQUE,
    password  VARCHAR(100) NOT NULL,
    question  VARCHAR(255),
    answer    VARCHAR(255),
    date      DATE
);

CREATE TABLE product (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    prod_id    VARCHAR(100) NOT NULL UNIQUE,
    prod_name  VARCHAR(100) NOT NULL,
    type       VARCHAR(50),
    stock      INT,
    price      DOUBLE,
    status     VARCHAR(50),
    image      VARCHAR(500),
    date       DATE
);

CREATE TABLE customer (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT,
    prod_id      VARCHAR(100),
    prod_name    VARCHAR(100),
    type         VARCHAR(50),
    quantity     INT,
    price        DOUBLE,
    date         DATE,
    image        VARCHAR(500),
    em_username  VARCHAR(100)
);

CREATE TABLE receipt (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT,
    total        DOUBLE,
    date         DATE,
    em_username  VARCHAR(100)
);
```

> La connexion est configurée dans `database.java` :
> `jdbc:mysql://localhost/cafe` avec l'utilisateur `root` et un mot de passe vide. Adaptez-la à votre environnement.

---

## 🚀 Installation et lancement

### Prérequis
- **JDK 8** avec JavaFX (ex. Oracle JDK 8 ou Liberica JDK 8 *Full*)
- **MySQL** (ou MariaDB / XAMPP)
- **Apache NetBeans** (recommandé)

### Étapes

1. **Cloner le dépôt**
   ```bash
   git clone https://github.com/Andassa/CafeShopManagement.git
   cd CafeShopManagement
   ```

2. **Créer la base de données** en exécutant le script SQL ci-dessus.

3. **Ouvrir le projet** `cafeShopManagementSystem/` dans NetBeans.

4. **Ajouter les dépendances** : *Project Properties → Libraries → Add JAR/Folder*
   et sélectionner tous les fichiers de `cafeShopManagementSystem/dist/lib/`.

5. **Adapter le chemin du reçu** dans `mainFormController.java` (méthode `menuReceiptBtn`)
   pour pointer vers `src/cafeshopmanagementsystem/report.jrxml` sur votre machine.

6. **Lancer** l'application avec *Run Project* (F6).

### Lancement direct du `.jar`

```bash
cd cafeShopManagementSystem/dist
java -jar cafeShopManagementSystem.jar
```

---

## 👤 Auteur

**Andassa** — [github.com/Andassa](https://github.com/Andassa)
