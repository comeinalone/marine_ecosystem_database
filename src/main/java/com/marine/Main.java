package com.marine;

import com.marine.db.*;
import com.marine.ui.*;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.sql.Connection;
import java.util.Arrays;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        try {
            DatabaseConfig config = DatabaseConfig.load();
            Class.forName("com.mysql.cj.jdbc.Driver");
            Database database = new Database(config);
            if (Arrays.asList(args).contains("--check-config")) {
                System.out.println("Configuration loaded; MySQL JDBC driver available. No connection attempted.");
                return;
            }
            if (Arrays.asList(args).contains("--check-db")) {
                try (Connection connection = database.open()) {
                    System.out.println("Connected to " + connection.getMetaData().getDatabaseProductName()
                            + " " + connection.getMetaData().getDatabaseProductVersion());
                }
                return;
            }
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("A graphical desktop session is required. Use --check-config for an offline check.");
                System.exit(1);
            }
            SwingUtilities.invokeLater(() -> {
                UiStyle.install();
                MainFrame frame = new MainFrame(database); frame.setVisible(true); frame.navigate("Dashboard");
            });
        } catch (Exception ex) {
            System.err.println(Errors.message(ex));
            System.exit(1);
        }
    }
}
