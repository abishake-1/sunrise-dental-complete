package com.sunrisedental.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Main window with one tab per module. The Reports tab is only added for administrators. */
final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    MainFrame(ApiClient api) {
        super("Sunrise Dental Clinic");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Appointments", new AppointmentsPanel(api));
        tabs.addTab("Patients", new PatientsPanel(api));
        tabs.addTab("Billing", new BillingPanel(api));
        if (api.isAdmin()) {
            tabs.addTab("Reports", new ReportsPanel(api));
        }

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(new JLabel("Signed in: " + api.fullName() + " (" + api.role() + ")"));
        JButton logout = new JButton("Log out");
        logout.addActionListener(e -> signOut(api));
        top.add(logout);

        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                api.logout();
                dispose();
                System.exit(0);
            }
        });
        setMinimumSize(new Dimension(900, 560));
        setSize(1000, 640);
        setLocationRelativeTo(null);
    }

    private void signOut(ApiClient api) {
        Async.run(this, () -> {
            api.logout();
            return Boolean.TRUE;
        }, done -> {
            new LoginFrame(api).setVisible(true);
            dispose();
        });
    }
}
