package com.routex.web;

import com.routex.service.BookingService;
import com.routex.service.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/** Public pages: home and bus search (§4.1, REQ-4.1.1 / REQ-4.1.2). */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final BusService busService;
    private final BookingService bookingService;

    @GetMapping("/")
    public String home(Model model) {
        List<String> cities = busService.getAvailableCities();
        model.addAttribute("totalBookings", bookingService.getTotalBookings());
        model.addAttribute("routeCount", busService.getAllBuses().size());
        model.addAttribute("cityCount", cities.size());
        return "index";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         Model model) {
        model.addAttribute("cities", busService.getAvailableCities());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("date", date);

        boolean searched = from != null || to != null || date != null;
        if (!searched) return "search";

        if (from == null || from.isBlank() || to == null || to.isBlank() || date == null) {
            model.addAttribute("error", "Please fill all search fields.");
        } else if (from.equalsIgnoreCase(to)) {
            model.addAttribute("error", "Source and destination cannot be the same.");
        } else if (date.isBefore(LocalDate.now())) {
            model.addAttribute("error", "Travel date cannot be in the past.");
        } else {
            model.addAttribute("results", busService.searchWithAvailability(from, to, date));
        }
        return "search";
    }
}
