package in.smartvrms.desktop.ui;

import in.smartvrms.application.booking.DraftValidator;
import in.smartvrms.contracts.StaffSession;
import in.smartvrms.desktop.controller.BookingController;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Navigation presents workflow, never trusted authorization. */
public final class WorkspaceView extends JPanel {
    private final CardLayout pages = new CardLayout();
    private final JPanel content = new JPanel(pages);
    public final BookingView booking;
    public final RunningServicesView runningServices;
    public final FinanceView financeView;
    public final AutomationAndReportsView automationReportsView;
    private final ButtonGroup navigation = new ButtonGroup();
    private final JLabel headerTitle;
    private final JLabel headerStatus;
    private final JButton exitButton;
    private final List<String> managementItems = List.of("Personnel", "Finance", "Reports", "Settings");
    private StaffSession session;

    public WorkspaceView(Runnable signIn) {
        this(signIn, null);
    }

    public WorkspaceView(Runnable signIn, StaffSession initialSession) {
        super(new BorderLayout());
        this.session = initialSession;
        booking = new BookingView(() -> showPage("Vehicles"));
        runningServices = new RunningServicesView(initialSession);
        financeView = new FinanceView(initialSession);
        automationReportsView = new AutomationAndReportsView(initialSession);

        JPanel sidebar = new JPanel(new BorderLayout(0, 20));
        sidebar.setPreferredSize(new Dimension(198, 0));
        sidebar.setBackground(Design.NAV);
        sidebar.setBorder(new EmptyBorder(28, 16, 20, 16));

        JPanel brand = Design.vertical();
        brand.add(Design.label("smart vrms", 23, Color.WHITE));
        brand.add(Box.createVerticalStrut(6));
        brand.add(Design.label("RENTAL OPERATIONS", 10, new Color(0xA6B7C1)));
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel links = Design.vertical();
        group(links, "DAILY WORK", new String[]{"Overview", "New booking", "Running services"});
        group(links, "RECORDS", new String[]{"Vehicles", "Customers", "Drivers"});
        group(links, "MANAGEMENT", managementItems.toArray(new String[0]));

        JScrollPane nav = Design.scroll(Design.top(links));
        nav.getViewport().setBackground(Design.NAV);
        sidebar.add(nav);

        exitButton = Design.button(session == null ? "Back to sign-in" : "Sign out", false);
        exitButton.addActionListener(e -> signIn.run());
        sidebar.add(exitButton, BorderLayout.SOUTH);
        add(sidebar, BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout());
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setBorder(new EmptyBorder(14, 24, 14, 24));

        headerTitle = Design.label(sessionTitle(), 11, Design.PRIMARY);
        headerStatus = Design.label(sessionStatus(), 12, Design.MUTED);
        bar.add(headerTitle, BorderLayout.WEST);
        bar.add(headerStatus, BorderLayout.EAST);
        main.add(bar, BorderLayout.NORTH);
        main.add(content);
        add(main);

        content.add(overview(), "Overview");
        content.add(Design.page("Fleet", "Browse an example vehicle and carry it into a rental draft.",
                new FleetView(candidate -> { booking.shortlist(candidate); showPage("New booking"); })), "Vehicles");
        content.add(Design.page("New rental", "Customer, rental period and vehicle — reviewed together before confirmation.", booking), "New booking");
        content.add(Design.page("Running services", "Fictional interface examples; operational integration is incomplete.", PreviewSafety.readOnly(runningServices)), "Running services");
        content.add(Design.page("Customers", "Fictional customer and visit examples.", PreviewSafety.readOnly(new CustomersView())), "Customers");
        content.add(Design.page("Drivers", "Fictional driver examples.", PreviewSafety.readOnly(new DriversView(session))), "Drivers");

        new BookingController(booking, new DraftValidator());
        updateManagementPages();
        PreviewSafety.disableActions(runningServices);
        PreviewSafety.disableActions(financeView);
        PreviewSafety.disableActions(automationReportsView);
        showPage("Overview");
    }

    public void setSession(StaffSession newSession) {
        this.session = newSession;
        runningServices.setSession(newSession);
        financeView.setSession(newSession);
        automationReportsView.setSession(newSession);
        headerTitle.setText(sessionTitle());
        headerStatus.setText(sessionStatus());
        exitButton.setText(session == null ? "Back to sign-in" : "Sign out");
        updateManagementPages();
    }

    public StaffSession session() {
        return session;
    }

    private String sessionTitle() {
        if (session == null) return "WORKSPACE PREVIEW";
        return "STAFF WORKSPACE • " + session.role();
    }

    private String sessionStatus() {
        if (session == null) return "Not connected  ·  No live records";
        String accessLabel = session.isAdministrator() ? "Administrator access" : "Operational access";
        String email = session.email() != null && !session.email().isBlank() ? session.email() + "  ·  " : "";
        return email + accessLabel;
    }

    private void updateManagementPages() {
        for (String item : managementItems) {
            content.remove(findComponentByName(content, item));
            if ("Finance".equals(item)) {
                JPanel preview = PreviewSafety.readOnly(financeView);
                preview.setName("Finance");
                content.add(preview, "Finance");
                continue;
            }
            if ("Reports".equals(item)) {
                JPanel preview = PreviewSafety.readOnly(automationReportsView);
                preview.setName("Reports");
                content.add(preview, "Reports");
                continue;
            }
            if (session != null && session.isEmployee()) {
                content.add(restricted(item), item);
            } else if (session != null && session.isAdministrator()) {
                JComponent section = PreviewSafety.readOnly(administratorSection(item));
                section.setName(item);
                content.add(section, item);
            } else {
                content.add(unavailable(item), item);
            }
        }
        content.revalidate();
        content.repaint();
    }

    private Component findComponentByName(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (name.equals(c.getName())) return c;
        }
        return new JPanel();
    }

    private void group(JPanel links, String heading, String[] items) {
        links.add(Box.createVerticalStrut(16));
        links.add(Design.label(heading, 10, new Color(0xA6B7C1)));
        links.add(Box.createVerticalStrut(8));
        for (String item : items) {
            JToggleButton link = new JToggleButton(item);
            link.setUI(new javax.swing.plaf.basic.BasicButtonUI());
            link.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
            link.setHorizontalAlignment(SwingConstants.LEFT);
            link.setBorder(new EmptyBorder(10, 12, 10, 12));
            Runnable colors = () -> {
                link.setBackground(link.isSelected() ? new Color(0x304B55) : Design.NAV);
                link.setForeground(Color.WHITE);
            };
            link.getModel().addChangeListener(e -> colors.run());
            colors.run();
            navigation.add(link);
            links.add(link);
            links.add(Box.createVerticalStrut(2));
            link.addActionListener(e -> showPage(item));
            if (!item.equals("Overview") && !item.equals("Vehicles") && !item.equals("New booking") && !item.equals("Running services") && !item.equals("Customers") && !item.equals("Drivers") && !item.equals("Finance") && !item.equals("Reports")) {
                JComponent pageComponent = unavailable(item);
                pageComponent.setName(item);
                content.add(pageComponent, item);
            }
        }
    }

    public void showPage(String name) {
        pages.show(content, name);
        var buttons = navigation.getElements();
        while (buttons.hasMoreElements()) {
            var b = buttons.nextElement();
            b.setSelected(b.getText().equals(name));
        }
    }

    private JComponent overview() {
        JPanel sections = Design.vertical();
        JPanel welcome = Design.card("Start a rental", "Choose a vehicle, enter the customer and rental dates, then review the draft.");
        JButton begin = Design.button("New rental draft", true);
        begin.addActionListener(e -> showPage("New booking"));
        JButton fleet = Design.button("Browse fleet", false);
        fleet.addActionListener(e -> showPage("Vehicles"));
        welcome.add(Design.row(begin, fleet));
        sections.add(welcome);
        sections.add(Box.createVerticalStrut(18));

        JPanel work = Design.card("Today's work", session != null
                ? "Signed-in identity verified; these operational queues are still examples."
                : "Operational queues will appear after sign-in and service configuration.");

        for (String[] item : new String[][]{
                {"01", "Upcoming pickups", "Customer checks, vehicle handover and driver assignment"},
                {"02", "On rent", "Running rentals, extensions and return times"},
                {"03", "Returns & settlement", "Inspection, vehicle release and outstanding balances"}}) {
            JPanel row = new JPanel(new BorderLayout(18, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(16, 0, 16, 0));
            row.add(Design.label(item[0], 18, Design.PRIMARY), BorderLayout.WEST);
            JPanel copy = Design.vertical();
            copy.add(Design.label(item[1], 16, Design.INK));
            copy.add(Box.createVerticalStrut(6));
            copy.add(Design.note(item[2]));
            row.add(copy);
            row.add(Design.label("Preview only", 12, Design.MUTED), BorderLayout.EAST);
            work.add(row);
            work.add(new JSeparator());
        }
        sections.add(work);
        sections.add(Box.createVerticalStrut(18));
        sections.add(Design.note("This is a preview of the staff workspace. No operational totals or availability are inferred from the example data."));
        Design.WidthPanel page = new Design.WidthPanel(new BorderLayout());
        page.add(sections, BorderLayout.NORTH);
        return Design.page("Rental desk", "One company · Kerala · INR · Asia/Kolkata", Design.scroll(page));
    }

    private JComponent unavailable(String name) {
        JPanel card = Design.card(name, "This module is awaiting its connected service.");
        card.add(Design.note("No records have been loaded. This feature remains in the approved scope; the preview currently supports a fleet-to-rental-draft journey."));
        card.add(Box.createVerticalStrut(18));
        JButton draft = Design.button("Open rental draft", false);
        draft.addActionListener(e -> showPage("New booking"));
        card.add(draft);
        JPanel p = Design.page(name, "Service not connected", Design.top(card));
        p.setName(name);
        return p;
    }

    private JComponent restricted(String name) {
        JPanel card = Design.card(name, "Access Restricted to Administrators");
        card.add(Design.note("Your active account has role EMPLOYEE. Operational staff cannot view or modify personnel compensation, payroll, company margins, or sensitive financial reports (FR-ACC-001, FR-ACC-003)."));
        card.add(Box.createVerticalStrut(18));
        JButton fleet = Design.button("Return to fleet overview", false);
        fleet.addActionListener(e -> showPage("Vehicles"));
        card.add(fleet);
        JPanel p = Design.page(name, "Restricted Section", Design.top(card));
        p.setName(name);
        return p;
    }

    private JComponent administratorSection(String name) {
        if ("Personnel".equals(name)) {
            JPanel sections = Design.vertical();
            JPanel card = Design.card(name, "Administrator Personnel & Staff Provisioning");
            card.add(Design.note("Invite employee staff accounts and manage active memberships (FR-AUTH-002, FR-EMP-004)."));
            card.add(Box.createVerticalStrut(12));
            JTextField emailField = new JTextField(20);
            JTextField nameField = new JTextField(20);
            card.add(Design.row(Design.label("Staff Email:", 12, Design.INK), emailField));
            card.add(Box.createVerticalStrut(8));
            card.add(Design.row(Design.label("Full Name:", 12, Design.INK), nameField));
            card.add(Box.createVerticalStrut(12));
            JButton inviteBtn = Design.button("Send Staff Invitation", true);
            JLabel statusLabel = Design.label("", 12, Design.PRIMARY);
            inviteBtn.addActionListener(e -> {
                String email = emailField.getText().strip();
                if (email.isEmpty() || !email.contains("@")) {
                    statusLabel.setText("Please enter a valid staff email.");
                    return;
                }
                statusLabel.setText("Staff invitation recorded for " + email);
                emailField.setText("");
                nameField.setText("");
            });
            card.add(Design.row(inviteBtn, statusLabel));
            sections.add(card);
            sections.add(Box.createVerticalStrut(16));
            sections.add(new PersonnelView(session));

            Design.WidthPanel width = new Design.WidthPanel(new BorderLayout());
            width.add(sections, BorderLayout.NORTH);
            JPanel p = Design.page(name, "Authorized Administrator Access", Design.scroll(width));
            p.setName(name);
            return p;
        }

        JPanel card = Design.card(name, "Administrator Workspace");
        card.add(Design.note("Authenticated administrator session verified. Full personnel, audit and reporting features connected in Stage 2B."));
        card.add(Box.createVerticalStrut(18));
        JButton fleet = Design.button("Return to fleet overview", false);
        fleet.addActionListener(e -> showPage("Vehicles"));
        card.add(fleet);
        JPanel p = Design.page(name, "Authorized Administrator Access", Design.top(card));
        p.setName(name);
        return p;
    }
}
