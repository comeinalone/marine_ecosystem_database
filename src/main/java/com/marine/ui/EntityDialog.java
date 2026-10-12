package com.marine.ui;

import com.marine.model.*;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Form creation/validation only. Reference data is loaded before opening it. */
public final class EntityDialog extends JDialog {
    private final EntityType type;
    private final Map<Field, JComponent> inputs = new LinkedHashMap<>();
    private EntityRow result;

    public EntityDialog(Window owner, EntityType type, EntityRow original,
                        Map<String, List<LookupChoice>> lookups) {
        super(owner, (original == null ? "Add " : "Edit ") + type.title(), ModalityType.APPLICATION_MODAL);
        this.type = type;
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
            form.add(input, inputConstraints);
        }
        content.add(new JScrollPane(form), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel"); cancel.addActionListener(event -> dispose());
        JButton save = new JButton("Save"); save.addActionListener(event -> save());
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
            }
            combo.setToolTipText("Create the referenced record first if this list is empty.");
            return combo;
        }
        return new JTextField(value == null ? "" : value.toString(), 25);
    }

    private void save() {
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
    public Optional<EntityRow> result() { return Optional.ofNullable(result); }
}
