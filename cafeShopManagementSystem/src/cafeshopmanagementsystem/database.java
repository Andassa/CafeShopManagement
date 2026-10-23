/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cafeshopmanagementsystem;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 *
 * @author WINDOWS 10
 */
public class database {

    private static final String DB_URL = env("DB_URL", "jdbc:mysql://localhost/cafe?useSSL=false");
    private static final String DB_USER = env("DB_USER", "root");
    private static final String DB_PASSWORD = env("DB_PASSWORD", "");

    public static Connection connectDB() {

        try {

            Class.forName("com.mysql.jdbc.Driver");

            Connection connect = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            return connect;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

}
