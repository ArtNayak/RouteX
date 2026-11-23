# RouteX: Online Bus Booking System

A bus booking web application written **entirely in Java**: Spring Boot 3, Spring MVC with Thymeleaf templates, Spring Security, Spring Data JPA and an H2 database. There is no JavaScript anywhere. Every page is rendered on the server.

Customers search routes, pick seats on a seat map, book, get a ticket and a printable bill, and cancel. Admins manage the bus fleet and see bookings, users, revenue and seat occupancy.

## Features
- **Search** by route and date, with the real number of free seats for that date.
- **Seat map** drawn from the bus's actual seat count (2 + aisle + 2 per row). Booked seats are shown and cannot be selected.
- **Booking** of up to 6 seats, with a demo checkout, a ticket and a text bill.
- **My Bookings** with cancel (a confirmation step, then the seats are free again).
- **Accounts:** register, sign in with a form, sign out.
- **Admin dashboard:** totals and revenue, all bookings, add, edit and delete buses, seat occupancy by bus and date, user list.

## Run it
You need **Java 17+** and **Maven**.

```
mvn spring-boot:run
```
Open http://localhost:8080. Or build a jar: `mvn clean package`, then `java -jar target/routex-bus-booking-1.0.0.jar`.

Test data (20 buses) and the admin account are created on every start, because the default H2 database lives in memory.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@routex.com` | `admin123` |
| User | register any email | at least 6 characters |

**Change the admin password** before using this for anything real: set `ROUTEX_ADMIN_PASSWORD` (or `routex.admin.password` in `application.properties`) before the first start.

## Pages
| URL | Who | What |
|---|---|---|
| `/` | everyone | home |
| `/search?from=&to=&date=` | everyone | search results |
| `/buses/{id}/seats?date=` | everyone to view, signed-in users to book | seat map and booking form |
| `/login`, `/register` | everyone | sign in, create account |
| `/bookings` | signed in | my bookings |
| `/bookings/{ref}` | the booking's owner or an admin | ticket and bill |
| `/admin` | admin | dashboard and fleet management |

## Architecture
```
Browser (HTML + CSS only)
        |  HTTP forms
Spring MVC controllers (com.routex.web)  --  Thymeleaf templates (src/main/resources/templates)
        |
Services (com.routex.service)            --  booking rules, validation, bills
        |
Spring Data JPA repositories             --  H2 (switchable to MySQL)
```
Seat selection works without JavaScript: each seat is a real checkbox, styled by CSS.

## Project layout
```
src/main/java/com/routex/
  RouteXApplication.java
  config/SecurityConfig.java       form login, CSRF, role rules
  model/                           User, Bus, Booking entities
  repository/                      JPA repositories
  service/                         UserService, BusService, BookingService, DataSeeder
  web/                             page controllers (home/search, auth, seats/bookings, admin)
src/main/resources/
  templates/                       Thymeleaf pages and the shared layout fragment
  static/css/main.css              styles
  application.properties
src/test/java/com/routex/          BookingServiceTest, WebFlowTest
```

## What changed from the JavaScript version
The original had a Java REST backend with a JavaScript single-page app on top. The front end is now Java too: the REST controllers and the `static/js` files (`app.js`, `seats.js`, `ui.js`, `api.js`, `canvas.js`) were replaced by Thymeleaf pages. While converting, these bugs and weaknesses were fixed:

- **Anyone could cancel anyone's booking** (`isAdmin` was hard-coded to `true`), and **any signed-in user could read any booking** by its reference. Both now require the booking's owner or an admin.
- **Double booking was possible.** The "is the seat free?" check and the insert could race. Booking now locks the bus row for the transaction, and a test with 8 simultaneous requests for one seat confirms exactly one wins.
- **Seat numbers were not validated.** Seats outside the bus, duplicates, or more than 6 were accepted. Past travel dates were accepted too.
- **The price came from the client's side of the world.** The total is always computed on the server from the bus's price. The simulated payment reference is generated on the server as well, and no card or bank details are collected.
- **Booking references** came from `count() + 1`, which can collide. They are now built from the database id.
- **Security:** HTTP Basic with CSRF switched off was replaced by form login with CSRF protection on. The H2 web console, which was open to everyone, is off. The admin password is configurable and no longer written to the log.
- **The seat map always drew 40 seats**, even for 36- or 44-seat buses. It now follows the bus's real seat count.
- **The seat map no longer floats and tilts forever.** That endless 3D animation made the seats a moving click target.
- Emails are matched case-insensitively.

## Tests
```
mvn test
```
17 tests: the booking rules (including the concurrent-booking race, ownership, cancellation and seat validation) and the real web flow through the full security filter chain (login, CSRF, admin-only pages, booking, ticket ownership, fleet management).

## Notes and limits
- **Payment is a demo.** Checkout marks the booking paid with a simulated reference. Connect a real payment provider before taking real bookings.
- The default H2 database is in memory, so everything resets when the app stops. To keep data, switch to MySQL: in `application.properties` replace the H2 settings with a MySQL URL and driver, and add the `mysql-connector-j` dependency to `pom.xml`.
- The pages load the Space Grotesk and Instrument Serif fonts from Google Fonts. Offline, the browser falls back to default fonts.

## SRS requirements coverage
| Requirement | Implementation |
|---|---|
| REQ-4.1.1 date selection | `<input type="date">` on the search form |
| REQ-4.1.2 seat layout | `seats.html` seat map from the bus's seat count |
| REQ-4.1.3 no double booking | `BookingService.createBooking()` (row lock + availability check) |
| REQ-4.1.4 store details | `Booking` entity: passenger, seats, price, date |
| REQ-4.1.5 confirmation | `booking.html` ticket |
| REQ-4.2.1 / 4.2.2 seat management | `BusService.getBookedSeats()` and `areSeatsAvailable()` |
| REQ-4.3.1 / 4.3.2 bill | `BookingService.generateBill()`, shown on the ticket page |
| REQ-5.2.1 no data loss | `@Transactional` booking and cancellation |
| REQ-5.3.1 / 5.3.2 security | `SecurityConfig`: BCrypt passwords, form login, CSRF, owner-only bookings |
| §5.5 admin restriction | `/admin/**` requires `ROLE_ADMIN` |
