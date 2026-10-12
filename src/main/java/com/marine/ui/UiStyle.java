package com.marine.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class UiStyle {
    public static final Color NAVY = new Color(20, 45, 64);
    public static final Color TEAL = new Color(0, 112, 128);
    public static final Color BACKGROUND = new Color(244, 247, 249);
    private UiStyle() { }

    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) { /* Swing default is usable. */ }
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "ComboBox.font", "Table.font", "TabbedPane.font"})
            UIManager.put(key, font);
    }
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        label.setForeground(NAVY);
        return label;
    }
    public static void configure(JTable table) {
        table.setRowHeight(29);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }
    public static void sizeColumns(JTable table) {
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(165);
    }
    public static EmptyBorder padding() { return new EmptyBorder(20, 24, 20, 24); }
}
