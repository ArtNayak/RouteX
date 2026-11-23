# RouteX — Online Bus Booking System

A full-stack bus booking application implementing the IEEE SRS document.  
**Backend:** Java 17 + Spring Boot 3 | **Frontend:** HTML / CSS / Vanilla JS

---

## Project Structure

```
routex/
│
├── pom.xml                                  Maven build file
│
├── src/main/java/com/routex/
│   │
│   ├── RouteXApplication.java               Spring Boot entry point
│   │
│   ├── model/                               JPA Entities (Data Layer)
│   │   ├── User.java                        User — §2.3.1 / §2.3.2
│   │   ├── Bus.java                         Bus — §4.1 / §4.2
│   │   └── Booking.java                     Booking — §4.1 / §4.3
│   │
│   ├── repository/                          Spring Data JPA (Data Access Layer)
│   │   ├── UserRepository.java
│   │   ├── BusRepository.java
│   │   └── BookingRepository.java
│   │
│   ├── service/                             Business Logic Layer
│   │   ├── UserService.java                 Auth / registration
│   │   ├── BusService.java                  Search + seat availability
│   │   ├── BookingService.java              Booking + bill generation (§4.3)
│   │   └── DataSeeder.java                  Seeds 20 buses + admin account
│   │
│   ├── controller/                          REST API (Presentation Layer)
│   │   ├── AuthController.java              POST /api/auth/login|register
│   │   ├── BusController.java               GET  /api/buses/search|cities|seats
│   │   ├── BookingController.java           POST/GET/DELETE /api/bookings
│   │   └── AdminController.java             GET  /api/admin/stats|bookings|users
│   │
│   └── config/
│       └── SecurityConfig.java              Spring Security — REQ-5.3.1/5.3.2
│
├── src/main/resources/
│   ├── application.properties               DB + server config
│   │
│   └── static/                              Frontend (served by Spring Boot)
│       ├── index.html                       HTML shell — all pages
│       ├── css/
│       │   └── main.css                     All styles
│       └── js/
│           ├── api.js                       All backend API calls
│           ├── ui.js                        DOM helpers, toasts, router
│           ├── canvas.js                    3D particle background
│           ├── seats.js                     Seat map + selection logic
│           └── app.js                       Main controller + auth + search
```

---

## Architecture (IEEE SRS §2.1)

```
Browser (HTML/CSS/JS)
        ↕  HTTP / JSON
Spring Boot REST Controllers
        ↕
Service Layer (Java Business Logic)
        ↕
JPA Repositories
        ↕
H2 / MySQL Database
```

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Steps

```bash
# 1. Clone / unzip the project
cd routex

# 2. Build
mvn clean package -DskipTests

# 3. Run
mvn spring-boot:run
# or: java -jar target/routex-bus-booking-1.0.0.jar

# 4. Open browser
http://localhost:8080
```

### Switch to MySQL (Production)

Edit `src/main/resources/application.properties`:

```properties
# Comment out H2 section, uncomment MySQL section
spring.datasource.url=jdbc:mysql://localhost:3306/routexdb?useSSL=false&serverTimezone=UTC
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update
```

Add MySQL dependency to `pom.xml`:
```xml
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
</dependency>
```

---

## Demo Credentials

| Role  | Email               | Password  |
|-------|---------------------|-----------|
| Admin | admin@routex.com    | admin123  |
| User  | Register any email  | min 6 chars |

---

## REST API Reference

| Method | Endpoint                        | Auth     | Description                  |
|--------|---------------------------------|----------|------------------------------|
| POST   | /api/auth/register              | Public   | Create new account           |
| POST   | /api/auth/login                 | Public   | Login                        |
| GET    | /api/auth/me                    | User     | Current user info            |
| GET    | /api/buses/cities               | Public   | List all cities              |
| GET    | /api/buses/search?from=&to=     | Public   | Search buses by route        |
| GET    | /api/buses/{id}/seats?date=     | Public   | Get booked seats for a date  |
| POST   | /api/bookings                   | User     | Create booking               |
| GET    | /api/bookings                   | User     | My bookings list             |
| GET    | /api/bookings/{ref}             | User     | Booking detail + bill text   |
| DELETE | /api/bookings/{ref}             | User     | Cancel booking               |
| GET    | /api/admin/stats                | Admin    | Dashboard statistics         |
| GET    | /api/admin/bookings             | Admin    | All bookings                 |
| GET    | /api/admin/users                | Admin    | All users                    |

---

## SRS Requirements Coverage

| SRS Requirement        | Implementation                                              |
|------------------------|-------------------------------------------------------------|
| REQ-4.1.1 Calendar UI  | `<input type="date">` in search form                        |
| REQ-4.1.2 Seat Layout  | `seats.js` — 10×4 interactive 3D seat grid                 |
| REQ-4.1.3 No double-booking | `BookingService.areSeatsAvailable()` + DB check         |
| REQ-4.1.4 Store details | `Booking` entity — name, seat, price, date               |
| REQ-4.1.5 Confirmation  | `Confirmation.show()` — ticket card with ref             |
| REQ-4.2.1 Unique seats  | Seat numbers stored as comma-separated string per booking |
| REQ-4.2.2 Validate avail| `BusService.getBookedSeats()` via `BookingRepository`    |
| REQ-4.3.1 Bill contents | `BookingService.generateBill()` — name, seat, price, date |
| REQ-4.3.2 Display bill  | Ticket card rendered in `Confirmation.show()`            |
| REQ-5.1.1 Response ≤2s  | H2 in-memory DB; indexed queries                         |
| REQ-5.2.1 No data loss  | `@Transactional` on `BookingService.createBooking()`     |
| REQ-5.3.1 DB auth       | `SecurityConfig` — Spring Security with BCrypt           |
| REQ-5.3.2 Data security | HTTP Basic Auth; all endpoints authenticated except search|
| §5.5 Admin restriction  | `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints   |
| §2.3.2 Admin role       | `role` field on `User` entity; separate admin routes     |
