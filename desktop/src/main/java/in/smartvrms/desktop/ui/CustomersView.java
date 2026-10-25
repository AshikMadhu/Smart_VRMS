package in.smartvrms.desktop.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Customer profile repository and visit history view.
 * Note: Standalone customer creation is disabled. Customer creation occurs strictly within booking (FR-CUST-002).
 */
public final class CustomersView extends JPanel {
    public final JTextField searchField = new JTextField();
    public final JButton searchButton = Design.button("Search Customer", true);
    public final DefaultListModel<String> resultsModel = new DefaultListModel<>();
    public final JList<String> resultsList = new JList<>(resultsModel);

    // Selected customer profile view
    public final JLabel customerName = Design.label("Select a customer", 18, Design.INK);
    public final JLabel customerContact = Design.label("—", 13, Design.MUTED);
    public final DefaultListModel<String> visitHistoryModel = new DefaultListModel<>();
    public final JList<String> visitList = new JList<>(visitHistoryModel);

    public CustomersView() {
        super(new BorderLayout(16, 16));
        setOpaque(false);

        // Header notice for FR-CUST-002
        JPanel noticeCard = Design.card("Customer Identity & History Repository", "Customer records and verified visit logs (FR-CUST-001, FR-CUST-008).");
        noticeCard.add(Design.note("NOTICE: In accordance with FR-CUST-002, standalone customer creation is disabled. New customers are registered strictly within the active booking workflow to bind KYC identity with custody."));
        add(noticeCard, BorderLayout.NORTH);

        // Left: Search and Results
        JPanel leftPanel = Design.vertical();
        JPanel searchCard = Design.card("Lookup Customer", "Search by mobile number, email, or name.");
        searchCard.add(Design.field("Search query", searchField));
        searchCard.add(Box.createVerticalStrut(10));

        // Sample specimens
        resultsModel.addElement("Vikram Menon • +91 98470 11223 • Kochi");
        resultsModel.addElement("Sita Lakshmi • +91 94471 55667 • Trivandrum");

        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setFixedCellHeight(40);
        resultsList.setBorder(BorderFactory.createLineBorder(Design.BORDER));

        searchButton.addActionListener(e -> {
            String q = searchField.getText().strip().toLowerCase(java.util.Locale.ROOT);
            resultsModel.clear();
            if (q.contains("vikram") || q.contains("98470") || q.isEmpty()) {
                resultsModel.addElement("Vikram Menon • +91 98470 11223 • Kochi");
            }
            if (q.contains("sita") || q.contains("94471") || q.isEmpty()) {
                resultsModel.addElement("Sita Lakshmi • +91 94471 55667 • Trivandrum");
            }
        });

        searchCard.add(Design.row(searchButton));
        searchCard.add(Box.createVerticalStrut(14));
        searchCard.add(new JScrollPane(resultsList));
        leftPanel.add(searchCard);

        // Right: Customer Profile & Visit Timeline
        JPanel rightPanel = Design.vertical();
        JPanel profileCard = Design.card("Customer Profile & Visit Timeline", "Verified rental history and age-at-visit logs.");
        profileCard.add(customerName);
        profileCard.add(Box.createVerticalStrut(4));
        profileCard.add(customerContact);
        profileCard.add(Box.createVerticalStrut(16));
        profileCard.add(new JSeparator());
        profileCard.add(Box.createVerticalStrut(14));

        profileCard.add(Design.label("Visit History (Age recorded per visit - FR-CUST-008)", 14, Design.INK));
        profileCard.add(Box.createVerticalStrut(8));

        visitHistoryModel.addElement("2026-08-15 • Age at visit: 32 • Booking #VRMS-BK-0081 (Completed)");
        visitHistoryModel.addElement("2026-09-20 • Age at visit: 32 • Booking #VRMS-BK-0104 (Completed)");

        visitList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        visitList.setFixedCellHeight(36);
        visitList.setBorder(BorderFactory.createLineBorder(Design.BORDER));
        profileCard.add(new JScrollPane(visitList));

        resultsList.addListSelectionListener(e -> {
            String sel = resultsList.getSelectedValue();
            if (sel != null) {
                String[] parts = sel.split("•");
                customerName.setText(parts[0].strip());
                customerContact.setText(parts.length > 1 ? parts[1].strip() : "");
            }
        });

        rightPanel.add(profileCard);

        Design.WidthPanel widthLeft = new Design.WidthPanel(new BorderLayout());
        widthLeft.add(leftPanel, BorderLayout.NORTH);

        Design.WidthPanel widthRight = new Design.WidthPanel(new BorderLayout());
        widthRight.add(rightPanel, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Design.scroll(widthLeft), Design.scroll(widthRight));
        split.setBorder(null);
        split.setResizeWeight(0.45);
        split.setDividerSize(8);
        split.setDividerLocation(380);

        add(split, BorderLayout.CENTER);
    }
}
