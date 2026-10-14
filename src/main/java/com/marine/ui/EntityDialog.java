package com.marine.ui;

import com.marine.model.*;
import com.marine.search.*;
import com.marine.model.RolePermissions.Operation;
import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Form creation/validation only. Reference data is loaded before opening it. */
public final class EntityDialog extends JDialog {
    private final EntityType type;
    private final RoleContext roles;
    private final Operation operation;
    private final long revision;
    private final PropertyChangeListener roleListener;
    private final Map<Field, JComponent> inputs = new LinkedHashMap<>();
    private EntityRow result;
    private final SearchService searchService;
    private final java.util.List<SearchAutocomplete> autocomplete = new java.util.ArrayList<>();

    public EntityDialog(Window owner, EntityType type, EntityRow original,
                        Map<String, List<LookupChoice>> lookups) {
        this(owner, type, original, lookups, new RoleContext());
    }

    public EntityDialog(Window owner, EntityType type, EntityRow original,
                        Map<String, List<LookupChoice>> lookups, RoleContext roles) {
        this(owner, type, original, lookups, roles, null);
    }

    public EntityDialog(Window owner, EntityType type, EntityRow original,
                        Map<String, List<LookupChoice>> lookups, RoleContext roles, SearchService searchService) {
        super(owner, (original == null ? "Add " : "Edit ") + type.title(), ModalityType.APPLICATION_MODAL);
        this.searchService = searchService;
        this.type = type;
        this.roles = roles;
        operation = original == null ? Operation.CREATE : Operation.UPDATE;
        revision = roles.revision();
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(UiStyle.padding());
        content.add(new JLabel("* Required. Blank optional fields are stored as NULL."), BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        for (Field field : type.fields()) {
            JComponent input = createInput(field, original == null ? null : original.get(field.name()), lookups);
            if (original != null && field.key() && type != EntityType.CONTACT) input.setEnabled(false);
            inputs.put(field, input);
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0; labelConstraints.gridy = row; labelConstraints.anchor = GridBagConstraints.WEST;
            labelConstraints.insets = new Insets(8, 0, 8, 16);
            JLabel label = new JLabel(field.label() + (field.required() ? " *" : ""));
            label.setLabelFor(input);
            form.add(label, labelConstraints);
            GridBagConstraints inputConstraints = new GridBagConstraints();
            inputConstraints.gridx = 1; inputConstraints.gridy = row++; inputConstraints.weightx = 1;
            inputConstraints.fill = GridBagConstraints.HORIZONTAL; inputConstraints.insets = new Insets(8, 0, 8, 0);
            if (field.reference() != null && searchService != null) {
                JPanel reference = new JPanel(new BorderLayout(4, 4)); reference.add(input, BorderLayout.CENTER);
                JPanel picker = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                JTextField query = new JTextField(12); query.setToolTipText("Type a name for suggestions; choose a specific ID.");
                JButton find = new JButton("Advanced Search"); picker.add(query); picker.add(find);
                JComboBox<?> combo = (JComboBox<?>) input;
                java.util.function.Consumer<SearchResult.Hit> choose = hit -> {
                    @SuppressWarnings("unchecked") JComboBox<LookupChoice> choices = (JComboBox<LookupChoice>) combo;
                    int id = ((Number) hit.identity().values().iterator().next()).intValue();
                    LookupChoice choice = new LookupChoice(id, hit.label());
                    choices.addItem(choice); choices.setSelectedItem(choice);
                };
                SearchDefinition definition = SearchDefinition.entity(EntityType.valueOf(field.reference()));
                autocomplete.add(new SearchAutocomplete(query, searchService, definition, roles, choose));
                find.addActionListener(event -> AdvancedSearchDialog.choose(this, definition, searchService, roles).ifPresent(choose));
                boolean enabled = input.isEnabled(); query.setEnabled(enabled); find.setEnabled(enabled);
                reference.add(picker, BorderLayout.SOUTH); form.add(reference, inputConstraints);
            } else form.add(input, inputConstraints);
        }
        content.add(new JScrollPane(form), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel"); cancel.addActionListener(event -> dispose());
        JButton save = new JButton("Save"); save.addActionListener(event -> save());
        save.setEnabled(roles.allows(type, operation));
        roleListener = event -> { result = null; dispose(); };
        roles.addRoleListener(roleListener);
        buttons.add(cancel); buttons.add(save); content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content); getRootPane().setDefaultButton(save);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(700, Math.min(650, 200 + row * 52));
        setMinimumSize(new Dimension(520, 320));
        setLocationRelativeTo(owner);
    }

    private JComponent createInput(Field field, Object value, Map<String, List<LookupChoice>> lookups) {
        if (field.kind() == Field.Kind.CHOICE) {
            JComboBox<String> combo = new JComboBox<>();
            if (!field.required()) combo.addItem("");
            field.options().forEach(combo::addItem);
            if (value != null) combo.setSelectedItem(value.toString());
            return combo;
        }
        if (field.kind() == Field.Kind.REFERENCE) {
            JComboBox<LookupChoice> combo = new JComboBox<>();
            for (LookupChoice choice : lookups.getOrDefault(field.reference(), List.of())) combo.addItem(choice);
            combo.setSelectedIndex(-1);
            if (value instanceof Number number) {
                for (int i = 0; i < combo.getItemCount(); i++) {
                    if (combo.getItemAt(i).id() == number.intValue()) combo.setSelectedIndex(i);
                }
                if (combo.getSelectedIndex() < 0) {
                    LookupChoice existing = new LookupChoice(number.intValue(), "Current reference (use Advanced Search for details)");
                    combo.addItem(existing); combo.setSelectedItem(existing);
                }
            }
            combo.setToolTipText("Create the referenced record first if this list is empty.");
            return combo;
        }
        return new JTextField(value == null ? "" : value.toString(), 25);
    }

    private void save() {
        if (revision != roles.revision() || !roles.allows(type, operation)) return;
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<Field, JComponent> entry : inputs.entrySet()) {
                JComponent input = entry.getValue();
                Object value = input instanceof JComboBox<?> combo ? combo.getSelectedItem() : ((JTextField) input).getText();
                values.put(entry.getKey().name(), Validation.parse(entry.getKey(), value));
            }
            result = new EntityRow(type, values);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Check your input", JOptionPane.WARNING_MESSAGE);
        }
    }
    @Override public void dispose() {
        autocomplete.forEach(SearchAutocomplete::close);
        roles.removeRoleListener(roleListener);
        super.dispose();
    }
    public Optional<EntityRow> result() {
        return revision == roles.revision() && roles.allows(type, operation)
                ? Optional.ofNullable(result) : Optional.empty();
    }
}
