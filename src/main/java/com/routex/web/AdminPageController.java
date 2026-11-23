package com.routex.web;

import com.routex.model.Bus;
import com.routex.service.BookingService;
import com.routex.service.BusService;
import com.routex.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin dashboard (§2.3.2, §5.5). Every route here requires ROLE_ADMIN, see SecurityConfig.
 *
 *   GET  /admin                     stats, bookings, fleet, users, seat occupancy
 *   POST /admin/buses               add a bus
 *   POST /admin/buses/{id}          update a bus
 *   POST /admin/buses/{id}/delete   delete a bus (refused while it has bookings)
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPageController {

    private final BookingService bookingService;
    private final BusService busService;
    private final UserService userService;

    @Data
    public static class BusForm {
        @NotBlank(message = "Bus number is required")  private String busNumber;
        @NotBlank(message = "Operator is required")    private String operatorName;
        @NotBlank(message = "Bus type is required")    private String busType;
        @NotBlank(message = "From city is required")   private String fromCity;
        @NotBlank(message = "To city is required")     private String toCity;
        @NotBlank(message = "Departure time is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Departure must use HH:mm")
        private String departureTime;
        @NotBlank(message = "Arrival time is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Arrival must use HH:mm")
        private String arrivalTime;
        @NotBlank(message = "Duration is required")    private String duration;
        @NotNull(message = "Price is required") @Positive(message = "Price must be greater than 0")
        private Double pricePerSeat;
        @NotNull(message = "Seat count is required") @Min(value = 1, message = "At least 1 seat")
        @Max(value = 100, message = "At most 100 seats")
        private Integer totalSeats;

        static BusForm from(Bus bus) {
            BusForm f = new BusForm();
            f.busNumber = bus.getBusNumber();
            f.operatorName = bus.getOperatorName();
            f.busType = bus.getBusType();
            f.fromCity = bus.getFromCity();
            f.toCity = bus.getToCity();
            f.departureTime = bus.getDepartureTime();
            f.arrivalTime = bus.getArrivalTime();
            f.duration = bus.getDuration();
            f.pricePerSeat = bus.getPricePerSeat();
            f.totalSeats = bus.getTotalSeats();
            return f;
        }

        Bus toBus() {
            Bus bus = new Bus();
            bus.setBusNumber(busNumber);
            bus.setOperatorName(operatorName);
            bus.setBusType(busType);
            bus.setFromCity(fromCity);
            bus.setToCity(toCity);
            bus.setDepartureTime(departureTime);
            bus.setArrivalTime(arrivalTime);
            bus.setDuration(duration);
            bus.setPricePerSeat(pricePerSeat);
            bus.setTotalSeats(totalSeats);
            return bus;
        }
    }

    @GetMapping
    public String dashboard(@RequestParam(required = false) Long editBus,
                            @RequestParam(required = false) Long seatBus,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate seatDate,
                            Model model) {
        List<Bus> buses = busService.getAllBuses();

        model.addAttribute("totalBookings", bookingService.getTotalBookings());
        model.addAttribute("totalRevenue", bookingService.getTotalRevenue());
        model.addAttribute("totalBuses", buses.size());
        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("bookings", bookingService.getAllBookings());
        model.addAttribute("buses", buses);

        // add / edit form
        Bus editing = editBus == null ? null : buses.stream().filter(b -> b.getId().equals(editBus)).findFirst().orElse(null);
        model.addAttribute("editingId", editing == null ? null : editing.getId());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", editing == null ? new BusForm() : BusForm.from(editing));
        }

        // seat occupancy monitor
        LocalDate date = seatDate == null ? LocalDate.now() : seatDate;
        Bus monitored = seatBus == null
                ? (buses.isEmpty() ? null : buses.get(0))
                : buses.stream().filter(b -> b.getId().equals(seatBus)).findFirst().orElse(null);
        model.addAttribute("seatDate", date);
        model.addAttribute("seatBusId", monitored == null ? null : monitored.getId());
        if (monitored != null) {
            List<Integer> booked = busService.getBookedSeats(monitored.getId(), date).stream().sorted().toList();
            model.addAttribute("seatTotal", monitored.getTotalSeats());
            model.addAttribute("seatBooked", booked);
            model.addAttribute("seatAvailable", Math.max(monitored.getTotalSeats() - booked.size(), 0));
        }
        return "admin";
    }

    @PostMapping("/buses")
    public String addBus(@Valid @ModelAttribute("form") BusForm form, BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) return flashErrors(result, form, redirect, "/admin");
        try {
            busService.createBus(form.toBus());
            redirect.addFlashAttribute("success", "Bus added.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return flashError(e.getMessage(), form, redirect, "/admin");
        }
        return "redirect:/admin";
    }

    @PostMapping("/buses/{id}")
    public String updateBus(@PathVariable Long id, @Valid @ModelAttribute("form") BusForm form,
                            BindingResult result, RedirectAttributes redirect) {
        String back = "/admin?editBus=" + id;
        if (result.hasErrors()) return flashErrors(result, form, redirect, back);
        try {
            busService.updateBus(id, form.toBus());
            redirect.addFlashAttribute("success", "Bus updated.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return flashError(e.getMessage(), form, redirect, back);
        }
        return "redirect:/admin";
    }

    @PostMapping("/buses/{id}/delete")
    public String deleteBus(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            busService.deleteBus(id);
            redirect.addFlashAttribute("success", "Bus deleted.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errors", List.of(e.getMessage()));
        }
        return "redirect:/admin";
    }

    private String flashErrors(BindingResult result, BusForm form, RedirectAttributes redirect, String back) {
        redirect.addFlashAttribute("errors", result.getAllErrors().stream().map(e -> e.getDefaultMessage()).distinct().toList());
        redirect.addFlashAttribute("form", form);
        return "redirect:" + back;
    }

    private String flashError(String message, BusForm form, RedirectAttributes redirect, String back) {
        redirect.addFlashAttribute("errors", List.of(message));
        redirect.addFlashAttribute("form", form);
        return "redirect:" + back;
    }
}
