package com.marine.ui;

import com.marine.dao.EntityDao;
import com.marine.model.EntityType;
import javax.swing.*;
import java.awt.BorderLayout;

/** Related researcher data remains in its original normalized tables. */
public final class ResearchersPanel extends JPanel implements Refreshable {
    private final JTabbedPane tabs = new JTabbedPane();
    public ResearchersPanel(EntityDao dao) {
        this(dao, new RoleContext());
    }
    public ResearchersPanel(EntityDao dao, RoleContext roles) {
        super(new BorderLayout());
        for (EntityType type : new EntityType[]{EntityType.RESEARCHER, EntityType.CONTACT, EntityType.BIOLOGIST, EntityType.GENETICIST})
            tabs.addTab(type.title(), new CrudPanel(type, dao, roles));
        add(tabs, BorderLayout.CENTER);
        tabs.addChangeListener(event -> refresh());
    }
    @Override public void refresh() { ((Refreshable) tabs.getSelectedComponent()).refresh(); }
}
