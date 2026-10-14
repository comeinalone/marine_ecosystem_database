package com.marine.ui;

import com.marine.model.*;
import com.marine.search.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static com.marine.ui.RoleUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SearchWindowTest {
    @Test void ambiguousAutocompleteShowsIdentifiedChoicesWithoutAutoAccepting() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Autocomplete focus/popups require a display");
        SearchDefinition definition = SearchDefinition.entity(EntityType.RESEARCHER);
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, params) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(2)
                : SearchJdbcStub.rows(definition, List.of(Map.of("researcherID", 1, "full_name", "Sana Khan", "email_address", "one@example.invalid"),
                Map.of("researcherID", 2, "full_name", "Sana Khan", "email_address", "two@example.invalid"))));
        JFrame frame = onEdt(JFrame::new); JTextField input = new JTextField(25);
        AtomicReference<SearchResult.Hit> accepted = new AtomicReference<>();
        SearchAutocomplete autocomplete = onEdt(() -> {
            frame.add(input); frame.pack(); frame.setVisible(true); input.requestFocusInWindow();
            return new SearchAutocomplete(input, new SearchService(jdbc), definition, new RoleContext(), accepted::set);
        });
        try {
            await(input::hasFocus); onEdt(() -> { input.setText("Sana Khan"); return null; });
            Field field = SearchAutocomplete.class.getDeclaredField("popup"); field.setAccessible(true);
            JPopupMenu popup = (JPopupMenu) field.get(autocomplete);
            await(() -> popup.isShowing() && popup.getComponentCount() == 2);
            onEdt(() -> {
                assertNull(accepted.get());
                assertTrue(((JMenuItem) popup.getComponent(0)).getText().contains("one@example.invalid"));
                ((JMenuItem) popup.getComponent(1)).doClick(); assertEquals(Map.of("researcherID", 2), accepted.get().identity()); return null;
            });
        } finally { onEdt(() -> { autocomplete.close(); frame.dispose(); return null; }); }
    }
    @Test void roleChangeClosesSearchDialogAndInvalidatesAnySelection() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Search dialogs require a display");
        onEdt(() -> {
            RoleContext roles = new RoleContext();
            AdvancedSearchDialog dialog = new AdvancedSearchDialog(null, SearchDefinition.entity(EntityType.RESEARCHER),
                    new SearchService(() -> { throw new AssertionError("No SQL expected"); }), roles);
            try {
                dialog.pack(); assertTrue(dialog.isDisplayable()); assertTrue(dialog.result().isEmpty());
                roles.setRole(UserRole.VIEWER); assertFalse(dialog.isDisplayable()); assertTrue(dialog.result().isEmpty());
            } finally { dialog.dispose(); }
            return null;
        });
    }
}
