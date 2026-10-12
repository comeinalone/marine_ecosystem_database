package com.marine.ui;

import com.marine.dao.ReportDao;
import com.marine.model.TableData;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;

public final class ReportsPanel extends JPanel implements Refreshable {
    private final ReportDao dao;
    private final JComboBox<ReportDao.Report> report = new JComboBox<>(ReportDao.Report.values());
    private final JButton refresh = new JButton("Refresh");
    private final JLabel status = new JLabel("Choose a report.");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private boolean busy;

    public ReportsPanel(ReportDao dao) {
        super(new BorderLayout(12, 14)); this.dao = dao;
        setBorder(UiStyle.padding()); setBackground(UiStyle.BACKGROUND);
        JPanel top = new JPanel(new BorderLayout(12, 12)); top.setOpaque(false);
        top.add(UiStyle.heading("Research Reports"), BorderLayout.NORTH);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); tools.setOpaque(false);
        tools.add(report); tools.add(refresh); tools.add(new JLabel("Search"));
        JTextField search = new JTextField(20); tools.add(search); top.add(tools, BorderLayout.SOUTH); add(top, BorderLayout.NORTH);
        UiStyle.configure(table);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model); table.setRowSorter(sorter); TableFilter.attach(search, sorter);
        add(new JScrollPane(table), BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        report.addActionListener(event -> refresh()); refresh.addActionListener(event -> refresh());
    }

    @Override public void refresh() {
        if (busy) return;
        ReportDao.Report selected = (ReportDao.Report) report.getSelectedItem();
        busy = true; report.setEnabled(false); refresh.setEnabled(false); status.setText("Loading report…");
        // Clear the preceding report so a failed refresh cannot mislabel stale data.
        model.setDataVector(new Object[0][0], new Object[0]);
        UiTasks.run(this, () -> dao.report(selected), this::show,
                () -> {
                    busy = false; report.setEnabled(true); refresh.setEnabled(true);
                    if (status.getText().equals("Loading report…")) status.setText("Report unavailable. Check setup and retry.");
                });
    }
    private void show(TableData data) {
        model.setDataVector(data.rows().stream().map(java.util.List::toArray).toArray(Object[][]::new), data.columns().toArray());
        UiStyle.sizeColumns(table); status.setText(data.rows().size() + " rows. Reports and audit history are read-only.");
    }
}
