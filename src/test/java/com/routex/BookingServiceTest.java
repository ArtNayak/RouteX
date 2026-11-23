package com.routex;

import static org.junit.jupiter.api.Assertions.*;

import com.routex.model.Booking;
import com.routex.model.Bus;
import com.routex.model.User;
import com.routex.repository.BusRepository;
import com.routex.service.BookingService;
import com.routex.service.BusService;
import com.routex.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

@SpringBootTest
class BookingServiceTest {

    @Autowired BookingService bookings;
    @Autowired BusService buses;
    @Autowired BusRepository busRepository;
    @Autowired UserService users;

    private static int dayOffset = 10;   // each test books a different date, so tests never share seats

    private static synchronized LocalDate freshDate() {
        return LocalDate.now().plusDays(dayOffset++);
    }

    private User newUser() {
        return users.register("Test User", "u-" + UUID.randomUUID() + "@example.com", "secret1");
    }

    private Bus bus() {
        return busRepository.findAll().stream().filter(b -> b.getBusNumber().equals("MH03")).findFirst().orElseThrow();  // 36 seats
    }

    private Booking book(User u, LocalDate date, Integer... seats) {
        return bookings.createBooking(u, bus().getId(), date, List.of(seats), "Asha", "9876543210", 30, "UPI");
    }

    @Test
    void priceIsComputedOnTheServerAndTheReferenceIsUnique() {
        LocalDate date = freshDate();
        Booking a = book(newUser(), date, 1, 2);
        Booking b = book(newUser(), date, 3);

        assertEquals(bus().getPricePerSeat() * 2, a.getTotalAmount());
        assertEquals("CONFIRMED", a.getStatus());
        assertTrue(a.getPaymentReference().startsWith("SIM-UPI-"));
        assertTrue(a.getBookingRef().matches("BK-\\d{8}-\\d{5}"), a.getBookingRef());
        assertNotEquals(a.getBookingRef(), b.getBookingRef());
    }

    @Test
    void aSeatCannotBeBookedTwiceForTheSameDateButCanOnAnotherDate() {
        LocalDate date = freshDate();
        book(newUser(), date, 5);
        assertThrows(IllegalStateException.class, () -> book(newUser(), date, 5, 6));
        assertDoesNotThrow(() -> book(newUser(), freshDate(), 5));
    }

    @Test
    void invalidSeatsAndDatesAreRejected() {
        User u = newUser();
        LocalDate date = freshDate();
        assertThrows(IllegalArgumentException.class, () -> book(u, date, 0));
        assertThrows(IllegalArgumentException.class, () -> book(u, date, 37));          // MH03 has 36 seats
        assertThrows(IllegalArgumentException.class, () -> book(u, date, 4, 4));         // duplicate
        assertThrows(IllegalArgumentException.class, () -> book(u, date, 1, 2, 3, 4, 5, 6, 7));  // more than 6
        assertThrows(IllegalArgumentException.class, () -> book(u, date));               // none
        assertThrows(IllegalArgumentException.class, () -> book(u, LocalDate.now().minusDays(1), 1));
        assertThrows(IllegalArgumentException.class, () ->
                bookings.createBooking(u, bus().getId(), date, List.of(8), "Asha", "9876543210", 30, "BITCOIN"));
    }

    @Test
    void onlyTheOwnerOrAnAdminCanCancelOrViewABooking() {
        User owner = newUser();
        User stranger = newUser();
        User admin = users.findByEmail("admin@routex.com").orElseThrow();
        Booking booking = book(owner, freshDate(), 10);

        assertThrows(AccessDeniedException.class, () -> bookings.cancelBooking(booking.getBookingRef(), stranger));
        assertThrows(AccessDeniedException.class, () -> bookings.getBookingFor(booking.getBookingRef(), stranger));
        assertEquals(booking.getBookingRef(), bookings.getBookingFor(booking.getBookingRef(), owner).getBookingRef());
        assertEquals(booking.getBookingRef(), bookings.getBookingFor(booking.getBookingRef(), admin).getBookingRef());

        assertEquals("CANCELLED", bookings.cancelBooking(booking.getBookingRef(), owner).getStatus());
        assertThrows(IllegalStateException.class, () -> bookings.cancelBooking(booking.getBookingRef(), admin));
    }

    @Test
    void cancellingFreesTheSeats() {
        LocalDate date = freshDate();
        User u = newUser();
        Booking booking = book(u, date, 20, 21);
        assertThrows(IllegalStateException.class, () -> book(newUser(), date, 21));
        bookings.cancelBooking(booking.getBookingRef(), u);
        assertDoesNotThrow(() -> book(newUser(), date, 21));
    }

    @Test
    void searchReportsRealAvailability() {
        LocalDate date = freshDate();
        book(newUser(), date, 1, 2, 3);
        Bus b = bus();
        var result = buses.searchWithAvailability(b.getFromCity(), b.getToCity(), date).stream()
                .filter(r -> r.bus().getId().equals(b.getId())).findFirst().orElseThrow();
        assertEquals(b.getTotalSeats() - 3, result.availableSeats());
    }

    /** Many people grabbing the same seat at the same moment: exactly one must win. */
    @Test
    void concurrentBookingsOfTheSameSeatNeverDoubleBook() throws Exception {
        LocalDate date = freshDate();
        int threads = 8;
        List<User> people = java.util.stream.IntStream.range(0, threads).mapToObj(i -> newUser()).toList();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (User u : people) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    book(u, date, 30);
                    success.incrementAndGet();
                } catch (IllegalStateException expected) {
                    // someone else got the seat first
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, success.get(), "exactly one booking may succeed");
        assertEquals(1, buses.getBookedSeats(bus().getId(), date).stream().filter(s -> s == 30).count());
    }
}
