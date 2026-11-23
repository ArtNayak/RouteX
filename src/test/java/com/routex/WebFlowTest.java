package com.routex;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.routex.model.Bus;
import com.routex.repository.BookingRepository;
import com.routex.repository.BusRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Drives the real server-rendered pages through the real security filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
class WebFlowTest {

    @Autowired MockMvc mvc;
    @Autowired BusRepository busRepository;
    @Autowired BookingRepository bookingRepository;

    private final LocalDate date = LocalDate.now().plusDays(40);

    private Bus bus() {
        return busRepository.findAll().stream().filter(b -> b.getBusNumber().equals("MH03")).findFirst().orElseThrow();
    }

    private String register() throws Exception {
        String email = "web-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Web Tester").param("email", email).param("password", "secret1"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        return email;
    }

    @Test
    void publicPagesRenderWithoutLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Travel")));
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
        mvc.perform(get("/search")).andExpect(status().isOk()).andExpect(content().string(containsString("Mumbai")));
        mvc.perform(get("/search").param("from", "Mumbai").param("to", "Pune").param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shivneri")))
                .andExpect(content().string(containsString("seats left")));
        mvc.perform(get("/search").param("from", "Pune").param("to", "Pune").param("date", date.toString()))
                .andExpect(content().string(containsString("cannot be the same")));
    }

    @Test
    void protectedPagesSendAnonymousUsersToLogin() throws Exception {
        mvc.perform(get("/bookings")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void formsWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/bookings").with(user("admin@routex.com").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/register").param("name", "x")).andExpect(status().isForbidden());
    }

    @Test
    void formLoginWorksAndWrongPasswordsAreRejected() throws Exception {
        String email = register();
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", "secret1"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", "wrong"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        // emails are case-insensitive
        mvc.perform(post("/login").with(csrf()).param("email", email.toUpperCase()).param("password", "secret1"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void duplicateRegistrationShowsAnErrorOnTheForm() throws Exception {
        String email = register();
        mvc.perform(post("/register").with(csrf()).param("name", "Again").param("email", email).param("password", "secret1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("already registered")));
    }

    @Test
    void adminAreaIsForAdminsOnly() throws Exception {
        String email = register();
        mvc.perform(get("/admin").with(user(email).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(user("admin@routex.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Admin Dashboard")));
        mvc.perform(post("/admin/buses/1/delete").with(user(email).roles("USER")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void seatMapShowsExactlyTheBusSeatsAndMarksBookedOnes() throws Exception {
        Bus bus = bus();   // 36 seats; the old UI always drew 40
        String html = mvc.perform(get("/buses/" + bus.getId() + "/seats").param("date", date.plusDays(1).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int seats = html.split("class=\"seat available\"", -1).length - 1;
        assertEquals(bus.getTotalSeats(), seats);
        mvc.perform(get("/buses/" + bus.getId() + "/seats")).andExpect(status().isNotFound());   // no date
        mvc.perform(get("/buses/" + bus.getId() + "/seats").param("date", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullBookingFlowWithOwnershipChecksAndServerSidePricing() throws Exception {
        Bus bus = bus();
        String owner = register();
        String stranger = register();
        LocalDate day = date.plusDays(2);

        // the browser also sends a tampered price; the server must ignore it
        MvcResult booked = mvc.perform(post("/bookings").with(user(owner).roles("USER")).with(csrf())
                        .param("busId", bus.getId().toString()).param("travelDate", day.toString())
                        .param("seats", "3").param("seats", "4")
                        .param("passengerName", "Asha").param("passengerPhone", "9876543210")
                        .param("passengerAge", "31").param("paymentMethod", "UPI")
                        .param("totalAmount", "1").param("pricePerSeat", "1"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = booked.getResponse().getRedirectedUrl();
        assertTrue(location.matches("/bookings/BK-\\d{8}-\\d{5}\\?new"), location);
        String ref = location.substring("/bookings/".length(), location.indexOf('?'));

        assertEquals(bus.getPricePerSeat() * 2, bookingRepository.findByBookingRef(ref).orElseThrow().getTotalAmount());

        // owner sees the ticket; a stranger does not
        mvc.perform(get("/bookings/" + ref).param("new", "").with(user(owner).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Booking Confirmed")))
                .andExpect(content().string(containsString("ROUTEX")));
        mvc.perform(get("/bookings/" + ref).with(user(stranger).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/bookings/" + ref + "/cancel").with(user(stranger).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());

        // the same seats are now taken: booking again bounces back to the seat page with an error
        mvc.perform(post("/bookings").with(user(stranger).roles("USER")).with(csrf())
                        .param("busId", bus.getId().toString()).param("travelDate", day.toString()).param("seats", "4")
                        .param("passengerName", "B").param("passengerPhone", "9876543210")
                        .param("passengerAge", "20").param("paymentMethod", "CARD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errors"))
                .andExpect(redirectedUrl("/buses/" + bus.getId() + "/seats?date=" + day));

        // bad input is reported, not stored
        mvc.perform(post("/bookings").with(user(owner).roles("USER")).with(csrf())
                        .param("busId", bus.getId().toString()).param("travelDate", day.toString()).param("seats", "9")
                        .param("passengerName", "").param("passengerPhone", "123").param("passengerAge", "0")
                        .param("paymentMethod", "CARD"))
                .andExpect(flash().attributeExists("errors"));

        // listed under My Bookings, then cancelled by the owner
        mvc.perform(get("/bookings").with(user(owner).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString(ref)));
        mvc.perform(post("/bookings/" + ref + "/cancel").with(user(owner).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/bookings"));
        assertEquals("CANCELLED", bookingRepository.findByBookingRef(ref).orElseThrow().getStatus());
    }

    @Test
    void adminCanAddEditAndDeleteABus() throws Exception {
        var admin = user("admin@routex.com").roles("ADMIN");
        String number = "T" + (int) (Math.random() * 90000 + 10000);
        mvc.perform(post("/admin/buses").with(admin).with(csrf())
                        .param("busNumber", number).param("operatorName", "TestOp").param("busType", "AC Seater")
                        .param("fromCity", "Alpha").param("toCity", "Beta").param("departureTime", "07:15")
                        .param("arrivalTime", "09:45").param("duration", "2h 30m").param("pricePerSeat", "150").param("totalSeats", "20"))
                .andExpect(redirectedUrl("/admin"));
        Bus created = busRepository.findAll().stream().filter(b -> b.getBusNumber().equals(number)).findFirst().orElseThrow();

        // invalid time is refused
        mvc.perform(post("/admin/buses/" + created.getId()).with(admin).with(csrf())
                        .param("busNumber", number).param("operatorName", "X").param("busType", "X").param("fromCity", "A")
                        .param("toCity", "B").param("departureTime", "25:99").param("arrivalTime", "09:00")
                        .param("duration", "1h").param("pricePerSeat", "10").param("totalSeats", "10"))
                .andExpect(flash().attributeExists("errors"));

        mvc.perform(post("/admin/buses/" + created.getId()).with(admin).with(csrf())
                        .param("busNumber", number).param("operatorName", "Renamed").param("busType", "AC Seater")
                        .param("fromCity", "Alpha").param("toCity", "Beta").param("departureTime", "08:00")
                        .param("arrivalTime", "10:00").param("duration", "2h").param("pricePerSeat", "175").param("totalSeats", "20"))
                .andExpect(redirectedUrl("/admin"));
        assertEquals("Renamed", busRepository.findById(created.getId()).orElseThrow().getOperatorName());

        mvc.perform(post("/admin/buses/" + created.getId() + "/delete").with(admin).with(csrf())).andExpect(redirectedUrl("/admin"));
        assertTrue(busRepository.findById(created.getId()).isEmpty());
    }

    @Test
    void aBusWithBookingsCannotBeDeleted() throws Exception {
        Bus bus = bus();
        String owner = register();
        mvc.perform(post("/bookings").with(user(owner).roles("USER")).with(csrf())
                .param("busId", bus.getId().toString()).param("travelDate", date.plusDays(5).toString()).param("seats", "12")
                .param("passengerName", "A").param("passengerPhone", "9876543210").param("passengerAge", "22").param("paymentMethod", "WALLET"));
        mvc.perform(post("/admin/buses/" + bus.getId() + "/delete").with(user("admin@routex.com").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attributeExists("errors"));
        assertTrue(busRepository.findById(bus.getId()).isPresent());
    }
}
