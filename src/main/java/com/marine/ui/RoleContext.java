package com.marine.ui;

import com.marine.model.EntityType;
import com.marine.model.RolePermissions;
import com.marine.model.RolePermissions.Operation;
import com.marine.model.UserRole;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Shared session state. Change roles on the Swing event thread; workers may read it. */
public final class RoleContext {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private volatile UserRole role = UserRole.ADMIN;
    private volatile long revision;

    public UserRole role() { return role; }
    public long revision() { return revision; }

    public void setRole(UserRole next) {
        Objects.requireNonNull(next, "Role is required");
        if (role == next) return;
        UserRole previous = role;
        role = next;
        revision++;
        changes.firePropertyChange("role", previous, next);
    }

    public boolean allows(EntityType entity, Operation operation) {
        return RolePermissions.allows(role, entity, operation);
    }

    public boolean canAccessPage(String page) { return RolePermissions.canAccessPage(role, page); }
    public void addRoleListener(PropertyChangeListener listener) { changes.addPropertyChangeListener("role", listener); }
    public void removeRoleListener(PropertyChangeListener listener) { changes.removePropertyChangeListener("role", listener); }
}
