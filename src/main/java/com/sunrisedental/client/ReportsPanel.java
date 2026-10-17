package com.sunrisedental.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Administrator reports with CSV export, plus a button that runs the reminder job by hand. */
final class ReportsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[][] REPORTS = {
        {"daily-appointments", "Daily appointments per dentist"},
        {"monthly-revenue", "Monthly revenue by treatment"},
        {"top-treatments", "Top treatments"},
        {"dentist-utilization", "Dentist utilization"},
        {"cancellation-rate", "Cancellation rate"}};

    private final transient ApiClient api;
    private final JComboBox<String> report = new JComboBox<>();
    private final JTextField from = new JTextField(LocalDate.now().withDayOfMonth(1).toString(), 9);
    private final JTextField to = new JTextField(LocalDate.now().toString(), 9);
    private final JTextField year = new JTextField(String.valueOf(LocalDate.now().getYear()), 5);
    private final JTextField month = new JTextField(String.valueOf(LocalDate.now().getMonthValue()), 3);
    private final JTextField limit = new JTextField("5", 3);
    private final DefaultTableModel model = new DefaultTableModel();

    ReportsPanel(ApiClient api) {
        super(new BorderLayout(8, 8));
        this.api = api;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (String[] r : REPORTS) {
            report.addItem(r[1]);
        }
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(report);
        top.add(new JLabel("From"));
        top.add(from);
        top.add(new JLabel("To"));
        top.add(to);
        top.add(new JLabel("Year"));
        top.add(year);
        top.add(new JLabel("Month"));
        top.add(month);
        top.add(new JLabel("Top N"));
        top.add(limit);
        JButton run = new JButton("Run");
        run.addActionListener(e -> run());
        JButton export = new JButton("Export CSV...");
        export.addActionListener(e -> export());
        JButton reminders = new JButton("Send tomorrow's reminders now");
        reminders.addActionListener(e -> reminders());
        top.add(run);
        top.add(export);
        top.add(reminders);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        add(new JLabel("Month/Year apply to the monthly report; From/To to the others; Top N to Top treatments."),
                BorderLayout.SOUTH);
    }

    private String slug() {
        return REPORTS[Math.max(0, report.getSelectedIndex())][0];
    }

    private Map<String, String> params() {
        Map<String, String> q = new LinkedHashMap<>();
        String slug = slug();
        if ("monthly-revenue".equals(slug)) {
            q.put("year", year.getText());
            q.put("month", month.getText());
        } else {
            q.put("from", from.getText());
            q.put("to", to.getText());
            if ("top-treatments".equals(slug)) {
                q.put("limit", limit.getText());
            }
        }
        return q;
    }

    @SuppressWarnings("unchecked")
    private void run() {
        String path = "/api/reports/" + slug();
        Map<String, String> q = params();
        Async.run(this, () -> api.getObject(path, q), table -> {
            List<String> columns = new ArrayList<>();
            if (table.get("columns") instanceof List<?> list) {
                for (Object c : list) {
                    columns.add(String.valueOf(c));
                }
            }
            model.setRowCount(0);
            model.setColumnIdentifiers(columns.toArray());
            if (table.get("rows") instanceof List<?> rows) {
                for (Object row : rows) {
                    Map<String, Object> r = (Map<String, Object>) row;
                    Object[] cells = new Object[columns.size()];
                    for (int i = 0; i < cells.length; i++) {
                        cells[i] = r.get(columns.get(i));
                    }
                    model.addRow(cells);
                }
            }
        });
    }

    private void export() {
        String path = "/api/reports/" + slug();
        Map<String, String> q = new LinkedHashMap<>(params());
        q.put("format", "csv");
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(slug() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        Async.run(this, () -> {
            byte[] csv = api.getRaw(path, q).body();
            try {
                Files.write(target.toPath(), csv);
            } catch (IOException e) {
                throw new ApiClientException(0, "FILE", "Could not save the file: " + e.getMessage(), List.of());
            }
            return target;
        }, file -> JOptionPane.showMessageDialog(this, "Saved " + file.getAbsolutePath()));
    }

    private void reminders() {
        Async.run(this, () -> api.post("/api/admin/reminders/run", null),
                r -> JOptionPane.showMessageDialog(this, "Reminders for " + r.get("date") + ": candidates="
                        + r.get("candidates") + ", sent=" + r.get("sent") + ", failed=" + r.get("failed")));
    }
}
