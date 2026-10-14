package com.marine.ui;

import com.marine.search.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.Map;
import java.util.function.Consumer;

/** Debounced MySQL suggestions. A suggestion is accepted only by an explicit action. */
final class SearchAutocomplete implements AutoCloseable {
    private final JTextField input;
    private final SearchRequests requests = new SearchRequests();
    private final Timer timer;
    private final JPopupMenu popup = new JPopupMenu();
    private final DocumentListener listener;
    private boolean choosing;
    SearchAutocomplete(JTextField input, SearchService service, SearchDefinition definition, RoleContext roles,
                       Consumer<SearchResult.Hit> accept) {
        this.input = input;
        timer = new Timer(300, event -> {
            if (!input.isShowing() || !input.hasFocus()) return;
            String term = input.getText();
            if (SearchCriteria.normalize(term).isEmpty()) return;
            SearchCriteria criteria;
            try { criteria = new SearchCriteria(Map.of(), term, true, null, false, 12, 0); }
            catch (IllegalArgumentException ex) { input.setToolTipText(ex.getMessage()); return; }
            requests.run(input, roles, () -> service.search(definition, criteria), result -> {
                popup.removeAll();
                for (SearchResult.Hit hit : result.hits()) {
                    JMenuItem item = new JMenuItem(hit.label());
                    item.addActionListener(action -> {
                        choosing = true;
                        try { accept.accept(hit); } finally { choosing = false; popup.setVisible(false); requests.invalidate(); }
                    }); popup.add(item);
                }
                if (result.total() > result.hits().size()) {
                    JMenuItem more = new JMenuItem("More matches — use Advanced Search"); more.setEnabled(false); popup.add(more);
                }
                if (input.isShowing() && input.hasFocus() && popup.getComponentCount() > 0) {
                    popup.setFocusable(false); popup.show(input, 0, input.getHeight());
                    input.requestFocusInWindow();
                }
            }, error -> { popup.setVisible(false); input.setToolTipText(Errors.message(error)); });
        }); timer.setRepeats(false);
        listener = new DocumentListener() {
            private void changed() { requests.invalidate(); popup.setVisible(false); timer.stop(); if (!choosing) timer.restart(); }
            public void insertUpdate(DocumentEvent event) { changed(); }
            public void removeUpdate(DocumentEvent event) { changed(); }
            public void changedUpdate(DocumentEvent event) { changed(); }
        };
        input.getDocument().addDocumentListener(listener);
        input.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "suggestions");
        input.getActionMap().put("suggestions", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (popup.getComponentCount() > 0 && input.isShowing()) {
                    popup.setFocusable(true); popup.show(input, 0, input.getHeight());
                    MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{popup, (JMenuItem) popup.getComponent(0)});
                }
            }
        });
    }
    public void close() { timer.stop(); requests.close(); popup.setVisible(false); input.getDocument().removeDocumentListener(listener); }
}
