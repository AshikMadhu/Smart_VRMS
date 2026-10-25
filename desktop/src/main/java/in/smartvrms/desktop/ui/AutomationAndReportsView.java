package in.smartvrms.desktop.ui;

import in.smartvrms.contracts.DeliveryAttemptRecord;
import in.smartvrms.contracts.FinancialReportSummary;
import in.smartvrms.contracts.OperationalReportSummary;
import in.smartvrms.contracts.OutboxMessageRecord;
import in.smartvrms.contracts.ServiceReviewRecord;
import in.smartvrms.contracts.StaffSession;
import in.smartvrms.contracts.WorkerHealthStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Desktop UI for Stage 7: Durable Automation Outbox, Service Reviews, and Periodic Reports.
 * Satisfies:
 * - FR-DOC-003, FR-DOC-004: Document expiry scans and renewal invalidation
 * - FR-BOOK-011, FR-MSG-001, FR-MSG-002: Transactional outbox, delivery attempts, and resend protection
 * - FR-REV-001, FR-REV-002: Optional staff-recorded reviews with correction history
 * - FR-FIN-003, FR-FIN-005, FR-ACC-003: Operational and Admin-only financial period reports
 */
public final class AutomationAndReportsView extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("Asia/Kolkata"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    // Session State
    private StaffSession session;

    // Feedback status
    public final JLabel feedbackLabel = Design.label("", 12, Design.MUTED);

    // ==========================================
    // Tab 1: Automation & Outbox Components
    // ==========================================
    public final JLabel workerHealthLabel = Design.label("HEALTHY", 16, new Color(0x245B4D));
    public final JLabel pendingCountLabel = Design.label("0", 18, Design.INK);
    public final JLabel claimedCountLabel = Design.label("0", 18, Design.INK);
    public final JLabel acceptedCountLabel = Design.label("0", 18, new Color(0x245B4D));
    public final JLabel failedCountLabel = Design.label("0", 18, new Color(0x775B28));
    public final JLabel lastScanLabel = Design.label("Not scanned yet", 13, Design.MUTED);

    public final JButton scanRemindersBtn = Design.button("Scan Expiry Reminders (<72h)", false);
    public final JButton processBatchBtn = Design.button("Claim & Process Batch", true);
    public final JButton refreshOutboxBtn = Design.button("Refresh Outbox", false);

    public final DefaultTableModel outboxTableModel = new DefaultTableModel(
            new Object[]{"ID", "Event Type", "Recipient", "Role", "Subject", "Status", "Attempts", "Next Attempt"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int col) { return false; }
    };
    public final JTable outboxTable = new JTable(outboxTableModel);

    public final DefaultTableModel attemptsTableModel = new DefaultTableModel(
            new Object[]{"Attempt #", "Started At", "Provider", "Outcome", "Response Code", "Error / Details"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int col) { return false; }
    };
    public final JTable attemptsTable = new JTable(attemptsTableModel);

    // ==========================================
    // Tab 2: Service Reviews Components
    // ==========================================
    public final JLabel totalReviewsLabel = Design.label("0", 18, Design.INK);
    public final JLabel avgRatingLabel = Design.label("—", 18, new Color(0x245B4D));

    public final JButton recordReviewBtn = Design.button("+ Record Customer Review", true);
    public final JButton correctReviewBtn = Design.button("Correct Review (Admin)", false);
    public final JButton refreshReviewsBtn = Design.button("Refresh Reviews", false);

    public final DefaultTableModel reviewsTableModel = new DefaultTableModel(
            new Object[]{"ID", "Booking ID", "Rating", "Feedback", "Source", "Status", "Recorded At"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int col) { return false; }
    };
    public final JTable reviewsTable = new JTable(reviewsTableModel);

    // ==========================================
    // Tab 3: Period Reporting Components
    // ==========================================
    public final JComboBox<String> periodGranularityCombo = new JComboBox<>(new String[]{
            "Indian Financial Year (Apr–Mar)",
            "Monday-start Week",
            "Monthly Period",
            "Daily / Custom Range"
    });
    public final JTextField fromDateField = new JTextField(LocalDate.now().withDayOfMonth(1).toString(), 10);
    public final JTextField toDateField = new JTextField(LocalDate.now().toString(), 10);
    public final JButton generateReportBtn = Design.button("Generate Summary", true);
    public final JButton exportCsvBtn = Design.button("Export CSV", false);

    // Operational Metrics Labels (Visible to Employee & Admin)
    public final JLabel opPeriodLabel = Design.label("—", 14, Design.INK);
    public final JLabel opTotalBookingsLabel = Design.label("0", 16, Design.INK);
    public final JLabel opActiveRentalsLabel = Design.label("0", 16, Design.INK);
    public final JLabel opCompletedRentalsLabel = Design.label("0", 16, new Color(0x245B4D));
    public final JLabel opOverdueRentalsLabel = Design.label("0", 16, new Color(0x775B28));
    public final JLabel opMaintenanceCasesLabel = Design.label("0", 16, Design.INK);
    public final JLabel opReviewsCountLabel = Design.label("0", 16, Design.INK);
    public final JLabel opAvgRatingLabel = Design.label("—", 16, new Color(0x245B4D));

    // Financial Metrics Labels (Admin Only)
    public final JPanel financialContainer = new JPanel(new BorderLayout(12, 12));
    public final JLabel finPeriodLabel = Design.label("—", 14, Design.INK);
    public final JLabel finGrossBilledLabel = Design.label("₹0.00", 16, Design.INK);
    public final JLabel finCashCollectedLabel = Design.label("₹0.00", 16, new Color(0x245B4D));
    public final JLabel finReceivablesLabel = Design.label("₹0.00", 16, new Color(0x775B28));
    public final JLabel finDepositsHeldLabel = Design.label("₹0.00", 16, Design.PRIMARY);
    public final JLabel finMaintenanceExpensesLabel = Design.label("₹0.00", 16, Design.INK);
    public final JLabel finStaffCompensationLabel = Design.label("₹0.00", 16, Design.INK);
    public final JLabel finTotalExpensesLabel = Design.label("₹0.00", 16, Design.INK);
    public final JLabel finNetAccrualMarginLabel = Design.label("₹0.00", 18, new Color(0x245B4D));

    // Specimen lists for preview mode
    public final List<OutboxMessageRecord> specimenOutbox = new ArrayList<>();
    public final List<ServiceReviewRecord> specimenReviews = new ArrayList<>();

    public AutomationAndReportsView() {
        this(null);
    }

    public AutomationAndReportsView(StaffSession session) {
        super(new BorderLayout(16, 16));
        setBackground(Design.BACKGROUND);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        initUI();
        populateSpecimens();
        setSession(session);
    }

    public void setSession(StaffSession session) {
        this.session = session;
        boolean isAdmin = session != null && "ADMINISTRATOR".equalsIgnoreCase(session.role());

        scanRemindersBtn.setEnabled(isAdmin);
        scanRemindersBtn.setToolTipText(isAdmin ? "Trigger document expiry scan" : "Administrator role required");
        processBatchBtn.setEnabled(isAdmin);
        processBatchBtn.setToolTipText(isAdmin ? "Trigger outbox batch execution" : "Administrator role required");
        correctReviewBtn.setEnabled(isAdmin);
        correctReviewBtn.setToolTipText(isAdmin ? "Correct recorded feedback with versioning" : "Administrator role required");

        updateFinancialView(isAdmin);
    }

    private void initUI() {
        // Top Header
        JPanel header = Design.vertical();
        header.add(Design.label("Automation, Reviews & Periodic Reporting", 24, Design.INK));
        header.add(Box.createVerticalStrut(4));
        header.add(Design.note("Transactional outbox delivery, post-settlement customer reviews, and reconciliation reports (FR-MSG-001, FR-REV-001, FR-FIN-003)."));
        header.add(Box.createVerticalStrut(12));
        header.add(feedbackLabel);
        add(header, BorderLayout.NORTH);

        // Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        tabbedPane.addTab("Automation & Outbox", createOutboxTab());
        tabbedPane.addTab("Customer Reviews", createReviewsTab());
        tabbedPane.addTab("Periodic Reports", createReportsTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createOutboxTab() {
        JPanel panel = new Design.WidthPanel(new BorderLayout(16, 16));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Health & Queue Summary Card
        JPanel summaryCard = Design.card("Worker Health & Queue Status", "Background automation runs reliably even when laptop client disconnects.");
        JPanel metricsRow = new JPanel(new GridLayout(1, 5, 12, 12));
        metricsRow.setOpaque(false);
        metricsRow.add(createMetricBox("Worker Status", workerHealthLabel));
        metricsRow.add(createMetricBox("Pending Queue", pendingCountLabel));
        metricsRow.add(createMetricBox("Claimed / Leased", claimedCountLabel));
        metricsRow.add(createMetricBox("Accepted Sent", acceptedCountLabel));
        metricsRow.add(createMetricBox("Failed / Retrying", failedCountLabel));
        summaryCard.add(metricsRow);
        summaryCard.add(Box.createVerticalStrut(8));
        summaryCard.add(Design.row(Design.label("Last Expiry Scan: ", 12, Design.MUTED), lastScanLabel));

        // Action Buttons Row
        JPanel actionsRow = Design.row(scanRemindersBtn, processBatchBtn, refreshOutboxBtn);
        summaryCard.add(Box.createVerticalStrut(12));
        summaryCard.add(actionsRow);

        panel.add(summaryCard, BorderLayout.NORTH);

        // Tables Split: Outbox Messages & Delivery Attempts
        JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 12, 12));
        tablesPanel.setOpaque(false);

        JPanel outboxCard = Design.card("Transactional Outbox Messages", "Queued communications with lease-based deduplication and version invalidation.");
        JScrollPane outboxScroll = new JScrollPane(outboxTable);
        outboxScroll.setPreferredSize(new Dimension(800, 160));
        outboxCard.add(outboxScroll);
        tablesPanel.add(outboxCard);

        JPanel attemptsCard = Design.card("Durable Delivery Attempts", "Audit trail of provider attempts, response codes, and exponential backoff.");
        JScrollPane attemptsScroll = new JScrollPane(attemptsTable);
        attemptsScroll.setPreferredSize(new Dimension(800, 140));
        attemptsCard.add(attemptsScroll);
        tablesPanel.add(attemptsCard);

        panel.add(tablesPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createReviewsTab() {
        JPanel panel = new Design.WidthPanel(new BorderLayout(16, 16));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Summary Card
        JPanel summaryCard = Design.card("Customer Service Reviews", "Voluntary feedback recorded only after vehicle return AND full payment settlement (FR-REV-001).");
        JPanel metricsRow = new JPanel(new GridLayout(1, 2, 16, 16));
        metricsRow.setOpaque(false);
        metricsRow.add(createMetricBox("Total Feedback Collected", totalReviewsLabel));
        metricsRow.add(createMetricBox("Average Service Rating", avgRatingLabel));
        summaryCard.add(metricsRow);

        // Action Buttons Row
        JPanel actionsRow = Design.row(recordReviewBtn, correctReviewBtn, refreshReviewsBtn);
        summaryCard.add(Box.createVerticalStrut(12));
        summaryCard.add(actionsRow);

        panel.add(summaryCard, BorderLayout.NORTH);

        // Reviews Table Card
        JPanel tableCard = Design.card("Recorded Service Feedback History", "Authoritative customer sentiment with versioned correction audit (FR-REV-002).");
        JScrollPane reviewsScroll = new JScrollPane(reviewsTable);
        reviewsScroll.setPreferredSize(new Dimension(800, 260));
        tableCard.add(reviewsScroll);

        panel.add(tableCard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createReportsTab() {
        JPanel panel = new Design.WidthPanel(new BorderLayout(16, 16));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Filter Card
        JPanel filterCard = Design.card("Periodic Reconciliation Filter", "Indian Financial Year (1 Apr–31 Mar), Monday-start weeks, and reconciled ledger sums.");
        JPanel filterRow = Design.row(
                Design.label("Granularity:", 13, Design.INK),
                periodGranularityCombo,
                Box.createHorizontalStrut(12),
                Design.label("From:", 13, Design.INK),
                fromDateField,
                Design.label("To:", 13, Design.INK),
                toDateField,
                Box.createHorizontalStrut(12),
                generateReportBtn,
                exportCsvBtn
        );
        filterCard.add(filterRow);
        panel.add(filterCard, BorderLayout.NORTH);

        // Results Container: Operational (Employee & Admin) + Financial (Admin Only)
        JPanel resultsPanel = new JPanel(new GridLayout(2, 1, 16, 16));
        resultsPanel.setOpaque(false);

        // Operational Report Card
        JPanel opCard = Design.card("Operational Performance Summary", "Fleet utilization and rental operations. Contains zero payroll or company margin figures (FR-ACC-003).");
        JPanel opGrid = new JPanel(new GridLayout(2, 4, 12, 12));
        opGrid.setOpaque(false);
        opGrid.add(createMetricBox("Total Bookings", opTotalBookingsLabel));
        opGrid.add(createMetricBox("Active Rentals", opActiveRentalsLabel));
        opGrid.add(createMetricBox("Completed Rentals", opCompletedRentalsLabel));
        opGrid.add(createMetricBox("Overdue Rentals", opOverdueRentalsLabel));
        opGrid.add(createMetricBox("Maintenance Cases", opMaintenanceCasesLabel));
        opGrid.add(createMetricBox("Customer Reviews", opReviewsCountLabel));
        opGrid.add(createMetricBox("Average Review Score", opAvgRatingLabel));
        opGrid.add(createMetricBox("Active Period", opPeriodLabel));
        opCard.add(opGrid);
        resultsPanel.add(opCard);

        // Financial Report Card Container
        resultsPanel.add(financialContainer);

        panel.add(resultsPanel, BorderLayout.CENTER);
        return panel;
    }

    private void updateFinancialView(boolean isAdmin) {
        financialContainer.removeAll();
        if (isAdmin) {
            JPanel finCard = Design.card("Authoritative Financial Performance (Administrator Only)", "Reconciled ledger postings. Indian Financial Year (1 Apr – 31 Mar) and operating accrual margins.");
            JPanel finGrid = new JPanel(new GridLayout(2, 4, 12, 12));
            finGrid.setOpaque(false);
            finGrid.add(createMetricBox("Billed Revenue", finGrossBilledLabel));
            finGrid.add(createMetricBox("Cash Collected", finCashCollectedLabel));
            finGrid.add(createMetricBox("Receivables Due", finReceivablesLabel));
            finGrid.add(createMetricBox("Deposits Held", finDepositsHeldLabel));
            finGrid.add(createMetricBox("Maintenance Expenses", finMaintenanceExpensesLabel));
            finGrid.add(createMetricBox("Staff Compensation", finStaffCompensationLabel));
            finGrid.add(createMetricBox("Total Expenses", finTotalExpensesLabel));
            finGrid.add(createMetricBox("Net Operating Margin", finNetAccrualMarginLabel));
            finCard.add(finGrid);
            financialContainer.add(finCard, BorderLayout.CENTER);
        } else {
            JPanel lockedCard = Design.card("Financial Performance (Confidential)", "Restricted under Role-Based Access Control (FR-ACC-003, FR-FIN-005).");
            JLabel lockedLabel = Design.label("🔒 403 Forbidden: Company margins, expense breakdowns, and staff compensation are restricted to Administrator accounts.", 13, new Color(0x775B28));
            lockedCard.add(lockedLabel);
            financialContainer.add(lockedCard, BorderLayout.CENTER);
        }
        financialContainer.revalidate();
        financialContainer.repaint();
    }

    private JPanel createMetricBox(String title, JLabel valueLabel) {
        JPanel box = new JPanel(new BorderLayout(4, 4));
        box.setBackground(Design.SURFACE);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Design.BORDER),
                new EmptyBorder(10, 12, 10, 12)
        ));
        box.add(Design.label(title, 11, Design.MUTED), BorderLayout.NORTH);
        box.add(valueLabel, BorderLayout.CENTER);
        return box;
    }

    public void displayOperationalReport(OperationalReportSummary summary) {
        opTotalBookingsLabel.setText(String.valueOf(summary.totalBookingsCount()));
        opActiveRentalsLabel.setText(String.valueOf(summary.activeRentalsCount()));
        opCompletedRentalsLabel.setText(String.valueOf(summary.completedRentalsCount()));
        opOverdueRentalsLabel.setText(String.valueOf(summary.overdueRentalsCount()));
        opMaintenanceCasesLabel.setText(String.valueOf(summary.maintenanceCasesCount()));
        opReviewsCountLabel.setText(String.valueOf(summary.reviewsCount()));
        opAvgRatingLabel.setText(summary.averageReviewRating() > 0 ? String.format("%.1f / 5.0", summary.averageReviewRating()) : "—");
        opPeriodLabel.setText(DATE_FMT.format(summary.fromTime()) + " – " + DATE_FMT.format(summary.toTime()));
    }

    public void displayFinancialReport(FinancialReportSummary summary) {
        finPeriodLabel.setText(summary.periodLabel());
        finGrossBilledLabel.setText(formatPaise(summary.totalRevenueBilledPaise()));
        finCashCollectedLabel.setText(formatPaise(summary.totalCashCollectedPaise()));
        finReceivablesLabel.setText(formatPaise(summary.outstandingReceivablesPaise()));
        finDepositsHeldLabel.setText(formatPaise(summary.refundableDepositLiabilityPaise()));
        finMaintenanceExpensesLabel.setText(formatPaise(summary.maintenanceExpensesPaise()));
        finStaffCompensationLabel.setText(formatPaise(summary.compensationExpensesPaise()));
        finTotalExpensesLabel.setText(formatPaise(summary.totalOperatingExpensesPaise()));
        finNetAccrualMarginLabel.setText(formatPaise(summary.netOperatingAccrualMarginPaise()));
    }

    public void displayWorkerHealth(WorkerHealthStatus health) {
        workerHealthLabel.setText(health.isLagging() ? "LAGGING" : "HEALTHY");
        workerHealthLabel.setForeground(health.isLagging() ? new Color(0x775B28) : new Color(0x245B4D));
        pendingCountLabel.setText(String.valueOf(health.pendingMessagesCount()));
        claimedCountLabel.setText(String.valueOf(health.claimedMessagesCount()));
        acceptedCountLabel.setText(String.valueOf(health.acceptedMessagesCount()));
        failedCountLabel.setText(String.valueOf(health.failedMessagesCount()));
        lastScanLabel.setText(health.lastScanTime() != null ? TIME_FMT.format(health.lastScanTime()) : "Never");
    }

    private static String formatPaise(long paise) {
        return String.format("₹%,.2f", paise / 100.0);
    }

    private void populateSpecimens() {
        specimenOutbox.add(new OutboxMessageRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "BOOKING_CONFIRMED",
                "specimen-outbox-booking-1",
                "customer.vip@example.in",
                "CUSTOMER",
                "Booking Confirmation #VRMS-2026-0042",
                "booking_confirmation",
                "{}",
                null,
                "ACCEPTED",
                null,
                "worker-specimen-1",
                1,
                5,
                null,
                null,
                null,
                Instant.now().minusSeconds(3600)
        ));

        specimenReviews.add(new ServiceReviewRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                5,
                "Vehicle was impeccably clean and fuel tank was full. Seamless check-in.",
                "DESK_IN_PERSON",
                true,
                "RECORDED",
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                Instant.now().minusSeconds(7200)
        ));

        // Load into table models
        outboxTableModel.setRowCount(0);
        for (OutboxMessageRecord m : specimenOutbox) {
            outboxTableModel.addRow(new Object[]{
                    m.id().toString().substring(0, 8) + "...",
                    m.eventType(),
                    m.recipientEmail(),
                    m.recipientRole(),
                    m.subject(),
                    m.status(),
                    m.attemptsCount(),
                    m.nextAttemptAt() != null ? TIME_FMT.format(m.nextAttemptAt()) : "—"
            });
        }

        reviewsTableModel.setRowCount(0);
        for (ServiceReviewRecord r : specimenReviews) {
            reviewsTableModel.addRow(new Object[]{
                    r.id().toString().substring(0, 8) + "...",
                    r.bookingId().toString().substring(0, 8) + "...",
                    r.rating() + " ★",
                    r.feedbackText(),
                    r.communicationSource(),
                    r.status(),
                    TIME_FMT.format(r.recordedAt())
            });
        }
        totalReviewsLabel.setText(String.valueOf(specimenReviews.size()));
        avgRatingLabel.setText("5.0 ★");
    }
}
