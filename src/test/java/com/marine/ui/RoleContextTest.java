package com.marine.ui;

import com.marine.model.EntityType;
import com.marine.model.RolePermissions.Operation;
import com.marine.model.UserRole;
import org.junit.jupiter.api.Test;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoleContextTest {
    @Test void defaultsToAdministratorAndNotifiesOnlyForActualChanges() {
        RoleContext roles = new RoleContext();
        assertEquals(UserRole.ADMIN, roles.role());
        assertEquals(0, roles.revision());
        List<UserRole> changes = new ArrayList<>();
        PropertyChangeListener listener = event -> {
            assertEquals(event.getNewValue(), roles.role());
            changes.add((UserRole) event.getNewValue());
        };
        roles.addRoleListener(listener);
        roles.setRole(UserRole.ADMIN);
        roles.setRole(UserRole.RESEARCHER);
        assertTrue(roles.allows(EntityType.SAMPLE, Operation.CREATE));
        assertFalse(roles.allows(EntityType.SPECIES, Operation.UPDATE));
        roles.setRole(UserRole.VIEWER);
        assertFalse(roles.allows(EntityType.SAMPLE, Operation.DELETE));
        roles.setRole(UserRole.VIEWER);
        assertEquals(List.of(UserRole.RESEARCHER, UserRole.VIEWER), changes);
        assertEquals(2, roles.revision());
        roles.removeRoleListener(listener);
        roles.setRole(UserRole.ADMIN);
        assertEquals(2, changes.size());
        assertEquals(3, roles.revision());
    }

    @Test void missingRoleDoesNotChangeTheSession() {
        RoleContext roles = new RoleContext();
        assertThrows(NullPointerException.class, () -> roles.setRole(null));
        assertEquals(UserRole.ADMIN, roles.role());
        assertEquals(0, roles.revision());
    }
}
