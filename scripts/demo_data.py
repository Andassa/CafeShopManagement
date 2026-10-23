#!/usr/bin/env python3
"""Génère 6 semaines de ventes de démonstration (SQL sur la sortie standard).

Usage : python3 scripts/demo_data.py | mysql -h 127.0.0.1 -u root cafe

ATTENTION : vide les tables product, customer et receipt avant l'insertion.
"""
import datetime
import os
import random

JOURS = 42
GRAINE = 42
CAISSIERS = ["andassa", "rova"]
IMAGES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "database", "demo", "images")

# prod_id, nom, type, prix, stock actuel, image
PRODUITS = [
    ("D-001", "Espresso", "Drinks", 2.5, 140, "espresso.png"),
    ("D-002", "Cappuccino", "Drinks", 3.5, 95, "cappuccino.png"),
    ("D-003", "Latte", "Drinks", 3.8, 30, "latte.png"),
    ("D-004", "Chocolat chaud", "Drinks", 3.2, 45, "chocolat.png"),
    ("M-001", "Croissant", "Meals", 2.2, 14, "croissant.png"),
    ("M-002", "Sandwich", "Meals", 5.9, 40, "sandwich.png"),
    ("M-003", "Cheesecake", "Meals", 4.5, 9, "cheesecake.png"),
    ("M-004", "Muffin", "Meals", 2.8, 25, "muffin.png"),
]

# Affluence selon le jour de la semaine (lundi = 0)
AFFLUENCE = [0.8, 0.85, 0.9, 1.0, 1.2, 1.45, 1.3]


def sql(valeur):
    if isinstance(valeur, str):
        return "'" + valeur.replace("\\", "\\\\").replace("'", "''") + "'"
    return str(valeur)


def commande(rng, jour_index):
    """Un panier réaliste avec des associations fréquentes."""
    muffin_arrete = jour_index >= JOURS - 8
    panier = {}
    boisson = rng.choices(["Espresso", "Cappuccino", "Latte", "Chocolat chaud", None],
                          weights=[30, 30, 20, 10, 10])[0]
    if boisson:
        panier[boisson] = 2 if rng.random() < 0.15 else 1
    if boisson == "Cappuccino" and rng.random() < 0.62:
        panier["Croissant"] = 1
    if boisson == "Espresso":
        if rng.random() < 0.35:
            panier["Cheesecake"] = 1
        elif rng.random() < 0.15:
            panier["Croissant"] = 1
    if boisson in ("Latte", "Chocolat chaud") and not muffin_arrete and rng.random() < 0.45:
        panier["Muffin"] = 1
    if rng.random() < 0.22 or not panier:
        panier["Sandwich"] = 1
    return panier


def main():
    rng = random.Random(GRAINE)
    aujourdhui = datetime.date.today()
    produits = {p[1]: p for p in PRODUITS}
    images = os.path.normpath(IMAGES)

    print("SET NAMES utf8mb4;")
    print("DELETE FROM customer; DELETE FROM receipt; DELETE FROM product;")
    print("INSERT IGNORE INTO employee (username, password, question, answer, date) VALUES "
          "('andassa', 'cafe12345', 'What is your favorite food?', 'pizza', CURDATE()), "
          "('rova', 'cafe12345', 'What is your favorite Color?', 'vert', CURDATE());")
    for prod_id, nom, type_, prix, stock, image in PRODUITS:
        print("INSERT INTO product (prod_id, prod_name, type, stock, price, status, image, date) VALUES "
              f"({sql(prod_id)}, {sql(nom)}, {sql(type_)}, {stock}, {prix}, 'Available', "
              f"{sql(os.path.join(images, image))}, {sql(str(aujourdhui - datetime.timedelta(days=JOURS)))});")

    client = 0
    for i in range(JOURS):
        date = aujourdhui - datetime.timedelta(days=JOURS - 1 - i)
        attendu = 24 * AFFLUENCE[date.weekday()] * (1 + 0.004 * i)
        if i == JOURS - 10:
            attendu *= 0.3  # journée anormale (fermeture anticipée)
        if i == JOURS - 1:
            attendu *= 0.6  # journée en cours
        for _ in range(max(1, round(rng.gauss(attendu, attendu * 0.12)))):
            client += 1
            caissier = rng.choice(CAISSIERS)
            total = 0.0
            for nom, quantite in commande(rng, i).items():
                prod_id, _, type_, prix, _, image = produits[nom]
                ligne = round(prix * quantite, 2)
                total += ligne
                print("INSERT INTO customer (customer_id, prod_id, prod_name, type, quantity, price, date, image, em_username) "
                      f"VALUES ({client}, {sql(prod_id)}, {sql(nom)}, {sql(type_)}, {quantite}, {ligne}, "
                      f"{sql(str(date))}, {sql(os.path.join(images, image))}, {sql(caissier)});")
            print("INSERT INTO receipt (customer_id, total, date, em_username) VALUES "
                  f"({client}, {round(total, 2)}, {sql(str(date))}, {sql(caissier)});")


if __name__ == "__main__":
    main()
