CREATE DATABASE IF NOT EXISTS cafe;
USE cafe;

CREATE TABLE IF NOT EXISTS employee (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    username  VARCHAR(100) NOT NULL UNIQUE,
    password  VARCHAR(100) NOT NULL,
    question  VARCHAR(255),
    answer    VARCHAR(255),
    date      DATE
);

CREATE TABLE IF NOT EXISTS product (
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

CREATE TABLE IF NOT EXISTS customer (
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

CREATE TABLE IF NOT EXISTS receipt (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT,
    total        DOUBLE,
    date         DATE,
    em_username  VARCHAR(100)
);
