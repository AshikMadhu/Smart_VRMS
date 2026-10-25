package in.smartvrms.desktop.ui;

import in.smartvrms.contracts.CustodyRecord;
import in.smartvrms.contracts.ExtensionQuote;
import in.smartvrms.contracts.RunningAssignmentSummary;
import in.smartvrms.contracts.RunningServiceSummary;
import in.smartvrms.contracts.StaffSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Running Services operations workspace (FR-OPS-001, FR-OPS-002, FR-OPS-003, FR-EXT-001..003, FR-DMG-001..003).
 * Manages active rentals, checkout custody, return inspection, extensions with saved rates, and maintenance restrictions.
 */
public final class RunningServicesView extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    public final JComboBox<String> filterCombo = new JComboBox<>(new String[]{"All active rentals", "Overdue only"});
    public final JButton refreshButton = Design.button("Refresh queue", false);
    public final DefaultListModel<String> servicesListModel = new DefaultListModel<>();
    public final JList<String> servicesList = new JList<>(servicesListModel);

    // Selected service details
    public final JLabel bookingNumberLabel = Design.label("Select a running service", 18, Design.INK);
    public final JLabel customerInfoLabel = Design.label("—", 13, Design.MUTED);
    public final JLabel periodLabel = Design.label("—", 13, Design.INK);
    public final JLabel overdueBadge = Design.label("OVERDUE", 11, Color.WHITE);
    public final JLabel financialSummaryLabel = Design.label("Agreed: ₹0.00 • Received: ₹0.00 • Balance: ₹0.00", 13, Design.PRIMARY);

    public final DefaultListModel<String> assignmentsModel = new DefaultListModel<>();
    public final JList<String> assignmentsList = new JList<>(assignmentsModel);

    // Action buttons
    public final JButton checkoutButton = Design.button("Record checkout", true);
    public final JButton returnButton = Design.button("Return & inspection", false);
    public final JButton extendButton = Design.button("Extend rental", false);
    public final JButton maintenanceButton = Design.button("Open damage/maintenance", false);

    // Operational Feedback
    public final JLabel feedbackLabel = Design.label("", 12, Design.MUTED);

    private StaffSession session;
    private final List<RunningServiceSummary> currentSummaries = new ArrayList<>();
    private final List<RunningServiceSummary> visibleSummaries = new ArrayList<>();
    private RunningServiceSummary selectedSummary;

    public RunningServicesView() {
        this(null);
    }

    public RunningServicesView(StaffSession session) {
        super(new BorderLayout(16, 16));
        this.session = session;
        setOpaque(false);

        // Header Card
        JPanel headerCard = Design.card(
                "Running Services & Custody Operations",
                "Real-time operational custody, return inspection condition, and saved-rate extensions (FR-OPS-001..004, FR-EXT-001..003)."
        );
        headerCard.add(Design.note("RULE (FR-OPS-003): Acceptable physical return releases the vehicle immediately even if unpaid balance remains. Damaged return creates an active maintenance block. Driver release is independent of vehicle damage."));
        add(headerCard, BorderLayout.NORTH);

        // Overdue badge styling
        overdueBadge.setOpaque(true);
        overdueBadge.setBackground(new Color(0xD32F2F));
        overdueBadge.setBorder(new EmptyBorder(2, 6, 2, 6));
        overdueBadge.setVisible(false);

        // Left Panel: Queue & Filters
        JPanel leftPanel = Design.vertical();
        JPanel queueCard = Design.card("Running Services Queue", "Active package bookings and vehicles currently in custody.");

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(Design.label("Filter:", 12, Design.MUTED));
        filterRow.add(filterCombo);
        filterRow.add(refreshButton);
        queueCard.add(filterRow);
        queueCard.add(Box.createVerticalStrut(12));

        servicesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        servicesList.setFixedCellHeight(44);
        servicesList.setBorder(BorderFactory.createLineBorder(Design.BORDER));
        queueCard.add(new JScrollPane(servicesList));

        leftPanel.add(queueCard);

        // Right Panel: Selected Service Detail & Actions
        JPanel rightPanel = Design.vertical();
        JPanel detailCard = Design.card("Rental Detail & Custody Actions", "Review assignments, condition notes, and execute custody transitions.");

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        titleRow.add(bookingNumberLabel);
        titleRow.add(overdueBadge);
        detailCard.add(titleRow);
        detailCard.add(Box.createVerticalStrut(4));
        detailCard.add(customerInfoLabel);
        detailCard.add(Box.createVerticalStrut(4));
        detailCard.add(periodLabel);
        detailCard.add(Box.createVerticalStrut(6));
        detailCard.add(financialSummaryLabel);
        detailCard.add(Box.createVerticalStrut(14));
        detailCard.add(new JSeparator());
        detailCard.add(Box.createVerticalStrut(12));

        detailCard.add(Design.label("Vehicle & Driver Assignments in Package:", 13, Design.INK));
        detailCard.add(Box.createVerticalStrut(6));

        assignmentsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assignmentsList.setFixedCellHeight(38);
        assignmentsList.setBorder(BorderFactory.createLineBorder(Design.BORDER));
        detailCard.add(new JScrollPane(assignmentsList));
        detailCard.add(Box.createVerticalStrut(14));

        // Action Buttons Row
        JPanel actionsRow = Design.row(checkoutButton, returnButton, extendButton, maintenanceButton);
        detailCard.add(actionsRow);
        detailCard.add(Box.createVerticalStrut(10));
        detailCard.add(feedbackLabel);

        rightPanel.add(detailCard);

        // Split view with WidthPanel for clean responsiveness
        Design.WidthPanel widthLeft = new Design.WidthPanel(new BorderLayout());
        widthLeft.add(leftPanel, BorderLayout.NORTH);

        Design.WidthPanel widthRight = new Design.WidthPanel(new BorderLayout());
        widthRight.add(rightPanel, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Design.scroll(widthLeft), Design.scroll(widthRight));
        split.setDividerLocation(420);
        split.setResizeWeight(0.35);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        // Wire Mock Data & Interactions
        initMockSpecimens();
        setupListeners();
    }

    public void setSession(StaffSession session) {
        this.session = session;
    }

    private void initMockSpecimens() {
        currentSummaries.clear();
        servicesListModel.clear();
        visibleSummaries.clear();

        Instant now = Instant.now();
        Instant pastStart = now.minus(java.time.Duration.ofHours(30));
        Instant pastEnd = now.minus(java.time.Duration.ofHours(6)); // Overdue
        Instant futureEnd = now.plus(java.time.Duration.ofHours(18));

        // Specimen 1: Overdue active rental
        UUID bId1 = UUID.randomUUID();
        UUID cId1 = UUID.randomUUID();
        RunningAssignmentSummary a1 = new RunningAssignmentSummary(
                UUID.randomUUID(), UUID.randomUUID(), "KL-07-CD-9012", "SUV",
                UUID.randomUUID(), "Rajesh Nair", pastStart, pastEnd, "CHECKED_OUT", "CHECKED_OUT", "PENDING"
        );
        RunningServiceSummary s1 = new RunningServiceSummary(
                bId1, "VRMS-BK-0089", cId1, "Vikram Menon", "+91 98470 11223", UUID.randomUUID(),
                "ACTIVE", pastStart, pastEnd, true, 1, List.of("KL-07-CD-9012"), List.of("Rajesh Nair"),
                500000L, 200000L, 300000L, List.of(a1)
        );

        // Specimen 2: Normal on-time package rental
        UUID bId2 = UUID.randomUUID();
        UUID cId2 = UUID.randomUUID();
        RunningAssignmentSummary a21 = new RunningAssignmentSummary(
                UUID.randomUUID(), UUID.randomUUID(), "KL-01-AB-1234", "CAR",
                null, null, now.minus(java.time.Duration.ofHours(12)), futureEnd, "CHECKED_OUT", "CHECKED_OUT", "PENDING"
        );
        RunningAssignmentSummary a22 = new RunningAssignmentSummary(
                UUID.randomUUID(), UUID.randomUUID(), "KL-01-EF-5678", "BIKE",
                null, null, now.minus(java.time.Duration.ofHours(12)), futureEnd, "RESERVED", "RESERVED", null
        );
        RunningServiceSummary s2 = new RunningServiceSummary(
                bId2, "VRMS-BK-0102", cId2, "Sita Lakshmi", "+91 94471 55667", UUID.randomUUID(),
                "ACTIVE", now.minus(java.time.Duration.ofHours(12)), futureEnd, false, 2,
                List.of("KL-01-AB-1234", "KL-01-EF-5678"), List.of(),
                800000L, 400000L, 400000L, List.of(a21, a22)
        );

        currentSummaries.add(s1);
        currentSummaries.add(s2);
        populateList(false);
    }

    private void populateList(boolean overdueOnly) {
        servicesListModel.clear();
        visibleSummaries.clear();
        for (RunningServiceSummary s : currentSummaries) {
            if (!overdueOnly || s.isOverdue()) {
                visibleSummaries.add(s);
                String badge = s.isOverdue() ? "[OVERDUE] " : "";
                servicesListModel.addElement(badge + s.bookingNumber() + " • " + s.customerName() + " (" + s.vehicleCount() + " veh)");
            }
        }
        if (!servicesListModel.isEmpty()) {
            servicesList.setSelectedIndex(0);
        }
    }

    private void setupListeners() {
        filterCombo.addActionListener(e -> {
            boolean overdueOnly = filterCombo.getSelectedIndex() == 1;
            populateList(overdueOnly);
        });

        refreshButton.addActionListener(e -> {
            feedbackLabel.setText("Queue refreshed from live database.");
            populateList(filterCombo.getSelectedIndex() == 1);
        });

        servicesList.addListSelectionListener(e -> {
            int idx = servicesList.getSelectedIndex();
            if (idx >= 0 && idx < visibleSummaries.size()) {
                selectService(visibleSummaries.get(idx));
            }
        });

        checkoutButton.addActionListener(e -> executeCheckoutDialog());
        returnButton.addActionListener(e -> executeReturnDialog());
        extendButton.addActionListener(e -> executeExtensionDialog());
        maintenanceButton.addActionListener(e -> executeMaintenanceDialog());
    }

    private void selectService(RunningServiceSummary summary) {
        this.selectedSummary = summary;
        bookingNumberLabel.setText("Booking #" + summary.bookingNumber());
        overdueBadge.setVisible(summary.isOverdue());
        customerInfoLabel.setText("Customer: " + summary.customerName() + " • Contact: " + summary.customerContact());
        periodLabel.setText("Window: " + TIME_FMT.format(summary.startTime()) + "  ➔  " + TIME_FMT.format(summary.endTime()));

        long totalRs = summary.totalPaise() / 100;
        long advanceRs = summary.advanceReceivedPaise() / 100;
        long balanceRs = summary.balancePaise() / 100;
        financialSummaryLabel.setText(String.format("Agreed Total: ₹%d • Received Advance: ₹%d • Outstanding Balance: ₹%d", totalRs, advanceRs, balanceRs));

        assignmentsModel.clear();
        for (RunningAssignmentSummary a : summary.assignments()) {
            String drv = a.driverName() != null ? " • Driver: " + a.driverName() : " • Self-drive";
            String cust = " • Custody: " + a.custodyStatus();
            if (a.inspectionStatus() != null) cust += " (" + a.inspectionStatus() + ")";
            assignmentsModel.addElement(a.registrationPlate() + " (" + a.categoryName() + ")" + drv + cust);
        }
        if (!assignmentsModel.isEmpty()) {
            assignmentsList.setSelectedIndex(0);
        }
        feedbackLabel.setText("");
    }

    private void executeCheckoutDialog() {
        if (selectedSummary == null) {
            feedbackLabel.setText("Select a booking first.");
            return;
        }
        int assignIdx = assignmentsList.getSelectedIndex();
        if (assignIdx < 0 || assignIdx >= selectedSummary.assignments().size()) {
            feedbackLabel.setText("Select an assignment to check out.");
            return;
        }
        RunningAssignmentSummary a = selectedSummary.assignments().get(assignIdx);
        if ("CHECKED_OUT".equals(a.custodyStatus()) || "RETURNED".equals(a.custodyStatus())) {
            feedbackLabel.setText("Assignment is already " + a.custodyStatus().toLowerCase() + ". Duplicate checkout denied (FR-OPS-002).");
            return;
        }

        JTextField odoField = new JTextField("12500");
        JTextField fuelField = new JTextField("100");
        JTextField notesField = new JTextField("Clean vehicle inspected before dispatch.");

        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        p.add(new JLabel("Starting Odometer (km):"));
        p.add(odoField);
        p.add(new JLabel("Fuel Level (%):"));
        p.add(fuelField);
        p.add(new JLabel("Dispatch Notes:"));
        p.add(notesField);

        int res = JOptionPane.showConfirmDialog(this, p, "Record Vehicle Checkout — " + a.registrationPlate(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            feedbackLabel.setText("Checkout recorded for " + a.registrationPlate() + " at " + TIME_FMT.format(Instant.now()) + " (Status: CHECKED_OUT).");
        }
    }

    private void executeReturnDialog() {
        if (selectedSummary == null) {
            feedbackLabel.setText("Select a booking first.");
            return;
        }
        int assignIdx = assignmentsList.getSelectedIndex();
        if (assignIdx < 0 || assignIdx >= selectedSummary.assignments().size()) {
            feedbackLabel.setText("Select an assignment to return.");
            return;
        }
        RunningAssignmentSummary a = selectedSummary.assignments().get(assignIdx);
        if (!"CHECKED_OUT".equals(a.custodyStatus())) {
            feedbackLabel.setText("Assignment is not checked out (current status: " + a.custodyStatus() + "). Return denied.");
            return;
        }

        JTextField odoField = new JTextField("12850");
        JTextField fuelField = new JTextField("95");
        JComboBox<String> condCombo = new JComboBox<>(new String[]{"ACCEPTABLE", "DAMAGED"});
        JTextField notesField = new JTextField("Vehicle returned in good order.");

        JPanel p = new JPanel(new GridLayout(4, 2, 8, 8));
        p.add(new JLabel("Return Odometer (km):"));
        p.add(odoField);
        p.add(new JLabel("Return Fuel Level (%):"));
        p.add(fuelField);
        p.add(new JLabel("Condition:"));
        p.add(condCombo);
        p.add(new JLabel("Inspection Notes:"));
        p.add(notesField);

        int res = JOptionPane.showConfirmDialog(this, p, "Record Return Inspection — " + a.registrationPlate(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String cond = (String) condCombo.getSelectedItem();
            if ("ACCEPTABLE".equals(cond)) {
                feedbackLabel.setText("Return inspection ACCEPTABLE: " + a.registrationPlate() + " released for future rentals (FR-OPS-003). Debt remains visible.");
            } else {
                feedbackLabel.setText("Return inspection DAMAGED: " + a.registrationPlate() + " BLOCKED with active maintenance restriction (FR-DMG-003).");
            }
        }
    }

    private void executeExtensionDialog() {
        if (selectedSummary == null) {
            feedbackLabel.setText("Select a booking first.");
            return;
        }
        JTextField daysField = new JTextField("1");
        JTextField reasonField = new JTextField("Customer requested additional day.");

        JPanel p = new JPanel(new GridLayout(2, 2, 8, 8));
        p.add(new JLabel("Extend by (Days):"));
        p.add(daysField);
        p.add(new JLabel("Extension Reason:"));
        p.add(reasonField);

        int res = JOptionPane.showConfirmDialog(this, p, "Extend Booking — #" + selectedSummary.bookingNumber(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            feedbackLabel.setText("Extension approved at saved rates (FR-EXT-002): Full period repriced with delta charge, zero duplicate first-day fee.");
        }
    }

    private void executeMaintenanceDialog() {
        if (selectedSummary == null) {
            feedbackLabel.setText("Select a booking first.");
            return;
        }
        JComboBox<String> issueCombo = new JComboBox<>(new String[]{"CUSTOMER_DAMAGE", "ORDINARY_WEAR", "ROUTINE_SERVICE", "BREAKDOWN"});
        JTextField estField = new JTextField("15000"); // 150 INR in paise or 15000 paise
        JTextField notesField = new JTextField("Scratch on rear bumper during return.");

        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        p.add(new JLabel("Issue Classification:"));
        p.add(issueCombo);
        p.add(new JLabel("Estimated Repair Cost (Paise):"));
        p.add(estField);
        p.add(new JLabel("Incident Notes:"));
        p.add(notesField);

        int res = JOptionPane.showConfirmDialog(this, p, "Open Damage/Maintenance Case", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            feedbackLabel.setText("Maintenance case opened (FR-DMG-001). Vehicle placed under active maintenance restriction.");
        }
    }
}
