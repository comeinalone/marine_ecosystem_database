package com.marine.ui;

import javax.swing.*;
import com.marine.search.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Shared styling and worker state. Superseded requests cannot repaint stale results. */
abstract class ResearchPanel extends JPanel implements Refreshable {
    protected final JPanel controls = new JPanel(new GridLayout(0, 1, 8, 6));
    protected final JLabel status = new JLabel("Select Refresh to load database records.");
    protected final JButton refreshButton = new JButton("Refresh");
    protected boolean busy;
    private long request;

    ResearchPanel(String title, String explanation) {
        super(new BorderLayout(12, 14));
        setBorder(UiStyle.padding()); setBackground(UiStyle.BACKGROUND);
        JPanel top = new JPanel(); top.setOpaque(false); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(UiStyle.heading(title)); top.add(Box.createVerticalStrut(8));
        JTextArea description = new JTextArea(explanation, 2, 30);
        description.setEditable(false); description.setFocusable(false); description.setOpaque(false);
        description.setLineWrap(true); description.setWrapStyleWord(true); description.setFont(UIManager.getFont("Label.font"));
        top.add(description);
        controls.setOpaque(false);
        JPanel refreshRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4)); refreshRow.setOpaque(false);
        refreshRow.add(refreshButton); controls.add(refreshRow); top.add(controls);
        add(top, BorderLayout.NORTH); add(status, BorderLayout.SOUTH);
        refreshButton.addActionListener(event -> refresh());
    }

    protected final <T> void load(Callable<T> query, Consumer<T> show) {
        long version = ++request;
        busy = true; refreshButton.setEnabled(false); status.setText("Loading database records…"); stateChanged();
        UiTasks.run(this, query, data -> { if (version == request) show.accept(data); }, () -> {
            if (version == request) { busy = false; refreshButton.setEnabled(true); stateChanged(); }
        }, error -> {
            if (version == request) status.setText(Errors.message(error));
        });
    }

    protected final void advanced(SearchService service, SearchDefinition definition, RoleContext roles,
                                  Consumer<SearchResult.Hit> select) {
        JButton button = new JButton("Advanced Search"); controls.add(button);
        button.addActionListener(event -> AdvancedSearchDialog.choose(this, definition, service, roles).ifPresent(select));
        roles.addRoleListener(event -> {
            request++; busy = false; refreshButton.setEnabled(true); status.setText("Role changed. Refresh to load records."); stateChanged();
        });
    }

    protected void stateChanged() { }
}
