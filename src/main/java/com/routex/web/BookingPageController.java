package com.routex.web;

import com.routex.model.Booking;
import com.routex.model.Bus;
import com.routex.model.User;
import com.routex.service.BookingService;
import com.routex.service.BusService;
import com.routex.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seat selection, booking, "My Bookings", ticket/bill and cancellation
 * (§4.1 Bus Search and Seat Booking, §4.2 Seat Management, §4.3 Bill Generation).
 */
@Controller
@RequiredArgsConstructor
public class BookingPageController {

    private static final int SEATS_PER_ROW = 4;   // 2 + aisle + 2

    private final BusService busService;
    private final BookingService bookingService;
    private final UserService userService;

    public record SeatView(int number, boolean booked) {}

    @Data
    public static class BookingForm {
        @NotNull
        private Long busId;

        @NotNull(message = "Travel date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate travelDate;

        @NotEmpty(message = "Select at least one seat")
        private List<Integer> seats = new ArrayList<>();

        @NotBlank(message = "Enter the passenger name")
        @Size(max = 80, message = "Passenger name is too long")
        private String passengerName;

        @NotBlank(message = "Enter a phone number")
        @Pattern(regexp = "^\\d{10}$", message = "Enter a valid 10-digit phone number")
        private String passengerPhone;

        @NotNull(message = "Enter the passenger's age")
        @Min(value = 1, message = "Enter a valid age")
        @Max(value = 120, message = "Enter a valid age")
        private Integer passengerAge;

        @NotBlank(message = "Choose a payment method")
        private String paymentMethod = "CARD";
    }

    // ----------------------------------------------------------------
    //  Seat map (REQ-4.1.2, REQ-4.2.1) — public to view, login needed to book
    // ----------------------------------------------------------------

    @GetMapping("/buses/{id}/seats")
    public String seatMap(@PathVariable Long id,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Model model) {
        if (date == null || date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Choose a travel date from today onwards.");
        }
        Bus bus = busService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bus not found: " + id));
        Set<Integer> booked = new HashSet<>(busService.getBookedSeats(id, date));

        // as many rows as the bus has seats for (the old front end always drew 40)
        List<List<SeatView>> rows = new ArrayList<>();
        for (int first = 1; first <= bus.getTotalSeats(); first += SEATS_PER_ROW) {
            List<SeatView> row = new ArrayList<>();
            for (int n = first; n < first + SEATS_PER_ROW && n <= bus.getTotalSeats(); n++) {
                row.add(new SeatView(n, booked.contains(n)));
            }
            rows.add(row);
        }

        model.addAttribute("bus", bus);
        model.addAttribute("date", date);
        model.addAttribute("rows", rows);
        model.addAttribute("bookedCount", booked.size());
        model.addAttribute("maxSeats", BookingService.MAX_SEATS_PER_BOOKING);
        if (!model.containsAttribute("form")) {
            BookingForm form = new BookingForm();
            form.setBusId(id);
            form.setTravelDate(date);
            User user = (User) model.getAttribute("currentUser");
            if (user != null) form.setPassengerName(user.getName());
            model.addAttribute("form", form);
        }
        return "seats";
    }

    // ----------------------------------------------------------------
    //  Create booking (REQ-4.1.3 – REQ-4.1.5). The price is computed on the server.
    // ----------------------------------------------------------------

    @PostMapping("/bookings")
    public String createBooking(@Valid @ModelAttribute("form") BookingForm form, BindingResult result,
                                Authentication auth, RedirectAttributes redirect) {
        if (form.getBusId() == null || form.getTravelDate() == null) {
            return "redirect:/search";
        }
        String back = "redirect:/buses/" + form.getBusId() + "/seats?date=" + form.getTravelDate();

        if (result.hasErrors()) {
            redirect.addFlashAttribute("errors", result.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage()).distinct().toList());
            redirect.addFlashAttribute("form", form);
            return back;
        }
        try {
            Booking booking = bookingService.createBooking(
                    currentUser(auth), form.getBusId(), form.getTravelDate(), form.getSeats(),
                    form.getPassengerName(), form.getPassengerPhone(), form.getPassengerAge(),
                    form.getPaymentMethod());
            return "redirect:/bookings/" + booking.getBookingRef() + "?new";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errors", List.of(e.getMessage()));
            redirect.addFlashAttribute("form", form);
            return back;
        }
    }

    // ----------------------------------------------------------------
    //  My bookings, ticket / bill, cancel
    // ----------------------------------------------------------------

    @GetMapping("/bookings")
    public String myBookings(Authentication auth, Model model) {
        model.addAttribute("bookings", bookingService.getBookingsByUser(currentUser(auth).getId()));
        return "bookings";
    }

    /** Ticket and plain-text bill (§4.3 REQ-4.3.1) — only for the booking's owner or an admin. */
    @GetMapping("/bookings/{ref}")
    public String bookingDetail(@PathVariable String ref, Authentication auth, Model model) {
        Booking booking = bookingService.getBookingFor(ref, currentUser(auth));
        model.addAttribute("booking", booking);
        model.addAttribute("bill", bookingService.generateBill(booking));
        return "booking";
    }

    @PostMapping("/bookings/{ref}/cancel")
    public String cancel(@PathVariable String ref, Authentication auth, RedirectAttributes redirect) {
        bookingService.cancelBooking(ref, currentUser(auth));
        redirect.addFlashAttribute("success", "Booking " + ref + " was cancelled and its seats are free again.");
        return "redirect:/bookings";
    }

    private User currentUser(Authentication auth) {
        return userService.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Your account no longer exists. Please sign in again."));
    }
}
