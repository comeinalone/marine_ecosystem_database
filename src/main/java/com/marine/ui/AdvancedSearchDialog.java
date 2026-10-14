package com.marine.ui;

import com.marine.search.*;
import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.Optional;

/** Modal identity picker; neither unique nor ambiguous results are selected automatically. */
public final class AdvancedSearchDialog extends JDialog {
    private final AdvancedSearchPanel panel;
    private final RoleContext roles;
    private final long revision;
    private final PropertyChangeListener roleListener;
    private SearchResult.Hit result;
    public AdvancedSearchDialog(Window owner, SearchDefinition definition, SearchService service, RoleContext roles) {
        super(owner, definition.title() + " — Advanced Search", ModalityType.APPLICATION_MODAL);
        this.roles = roles; revision = roles.revision();
        panel = new AdvancedSearchPanel(definition, service, roles, hit -> { result = hit; dispose(); }, this::dispose);
        roleListener = event -> { result = null; dispose(); }; roles.addRoleListener(roleListener);
        setContentPane(panel); getRootPane().setDefaultButton(panel.searchButton()); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1000, screen.width - 60), Math.min(680, screen.height - 80));
        setMinimumSize(new Dimension(620, 420)); setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    public Optional<SearchResult.Hit> result() { return revision == roles.revision() ? Optional.ofNullable(result) : Optional.empty(); }
    public static Optional<SearchResult.Hit> choose(Component owner, SearchDefinition definition, SearchService service, RoleContext roles) {
        AdvancedSearchDialog dialog = new AdvancedSearchDialog(SwingUtilities.getWindowAncestor(owner), definition, service, roles);
        dialog.setVisible(true); return dialog.result();
    }
    @Override public void dispose() { panel.close(); roles.removeRoleListener(roleListener); super.dispose(); }
}
