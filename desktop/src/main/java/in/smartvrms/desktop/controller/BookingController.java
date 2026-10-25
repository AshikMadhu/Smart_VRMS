package in.smartvrms.desktop.controller;

import in.smartvrms.application.booking.DraftValidator;
import in.smartvrms.contracts.BookingDraft;
import in.smartvrms.desktop.ui.BookingView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class BookingController {
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT);
    public BookingController(BookingView view, DraftValidator validator) {
        view.check.addActionListener(e -> {
            BookingDraft draft = new BookingDraft(view.customer.getText().strip(),view.phone.getText().strip(),parse(view.pickup.getText()),parse(view.expectedReturn.getText()),view.driver.isSelected());
            var issues = validator.validate(draft);
            view.feedback.setText(issues.isEmpty() ? "Fields are valid locally. Availability, pricing and identity are not checked. Nothing has been saved or booked." : String.join("\n",issues));
            if(issues.isEmpty()) view.showReview();
        });
        view.clear.addActionListener(e -> {
            view.customer.setText(""); view.phone.setText(""); view.pickup.setText(""); view.expectedReturn.setText(""); view.driver.setSelected(false);
            view.clearSelection();
            view.feedback.setText("Unsaved local draft cleared. No records were changed."); view.customer.requestFocusInWindow();
        });
    }
    private static LocalDateTime parse(String value) {
        try { return LocalDateTime.parse(value.strip(),INPUT); } catch (DateTimeParseException ex) { return null; }
    }
}
