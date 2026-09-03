# Practice API

Scheduling backend for a single-practitioner occupational therapy
practice. Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway.

It serves two audiences: the public booking flow on the marketing site,
and a private admin area for the practitioner.

---

## What this is, and is not

**Is:** availability, booking, appointments, patients, and the admin
operations around them.

**Is not:** a CMS. The public site's copy, imagery, biography and
credentials stay in the Angular project as static content. There is no
endpoint returning a biography, and none should be added — that content
belongs in version control, not a database.

There is also **no doctor-selection endpoint**. The practice has one
practitioner, appointments belong to her internally, and a patient is
never asked to choose.

---

## Running it

### Locally, without Docker (what this machine is set up for)

Requires a JDK 21 and PostgreSQL 16, both installed via Homebrew:

```bash
brew install openjdk@21 maven postgresql@16
brew services start postgresql@16

# One-off: create the role and database
createuser -s practice
createdb -O practice practice
```

Then:

```bash
cp .env.example .env     # fill in JWT_SECRET and ADMIN_PASSWORD
./dev.sh
```

`dev.sh` loads `.env`, checks PostgreSQL is actually accepting
connections before starting (the commonest reason a start fails), and
runs the app on `http://localhost:8080`.

Flyway creates the schema and seeds the practitioner, the working week
and the booking reasons on first start.

To browse the database in a browser at `http://localhost:8081`:

```bash
brew install pgweb
./dev-db.sh
```

### With Docker

`docker-compose.yml` and the `Dockerfile` are written and ready, but
have **not been run** — Docker is not installed on the development
machine this was built on. Treat them as unverified.

```bash
cp .env.example .env
# Set JWT_SECRET and ADMIN_PASSWORD. Generate a secret with:
#   openssl rand -base64 48
docker compose up --build
```

Compose brings up PostgreSQL, waits for its health check, then starts
the API on `http://localhost:8080`.

### The admin account

Created on first start from `ADMIN_EMAIL` and `ADMIN_PASSWORD`, then
never touched again. If `ADMIN_PASSWORD` is unset the application logs a
warning and creates nothing — there is deliberately no default password,
because a known default on a system holding patient contact details is
an open door.

Change the password after first sign-in.

---

## Configuration

Every value is an environment variable; see `.env.example`. The ones
that matter:

| Variable | Meaning |
| --- | --- |
| `DATABASE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL connection |
| `APP_DOCTOR_TIMEZONE` | The practice's local zone, e.g. `Europe/Paris` |
| `APP_SLOT_DURATION_MINUTES` | Appointment length (default 30) |
| `APP_MIN_LEAD_TIME_HOURS` | How far ahead booking must be (default 24) |
| `APP_MAX_HORIZON_DAYS` | How far ahead booking is open (default 90) |
| `JWT_SECRET` | Admin token signing key, **32+ characters** |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Bootstrap operator account |
| `CORS_ALLOWED_ORIGINS` | Exact origins allowed to call the API |

The application refuses to start without a `JWT_SECRET`, and refuses one
shorter than 32 characters: HS256 needs 256 bits, and a short key means
a forgeable signature.

---

## Database

Flyway owns the schema. `spring.jpa.hibernate.ddl-auto=validate`, so an
entity that drifts from a migration is a startup failure rather than a
runtime surprise.

```
V1__init.sql    tables, indexes, constraints
V2__seed.sql    one practitioner, a working week, booking reasons
```

To change the schema, add `V3__…sql`. Never edit an applied migration.

No patients or appointments are seeded. Fabricated people in a clinical
system become indistinguishable from real ones within a week of use.

---

## How availability works

`AvailabilityService` is the single source of truth. A slot is offered
only if **all** of these hold:

1. it falls inside a recurring working interval for that weekday,
2. it is not inside a blocked period,
3. it does not overlap an appointment that still occupies time,
4. it starts after the minimum lead time,
5. it starts before the booking horizon,
6. it fits entirely within its working interval.

Booking validates through the same method, so a slot the API offers is
one it will accept. They cannot drift, because they are the same code.

### Time and timezones

- Everything persisted is an `Instant` in a `TIMESTAMPTZ` column.
- Weekly working hours are stored as **local wall-clock** `TIME` plus a
  weekday. "Monday at nine" stays nine o'clock across a daylight-saving
  change; only the underlying UTC instant moves.
- The conversion happens per day through `APP_DOCTOR_TIMEZONE`.

`hibernate.jdbc.time_zone` is deliberately **not** set. It is redundant
for `TIMESTAMPTZ`, and it also shifts `TIME` columns — with it enabled a
09:00 interval read back as 11:00 in summer and the whole working day
silently moved. There is a test covering this.

---

## How booking works, and why it cannot double-book

Three problems, each solved in a specific place rather than by being
careful:

### 1. Two people, one slot

Any "check then insert" races: both requests can read the slot as free
before either writes. The actual guarantee is a PostgreSQL exclusion
constraint:

```sql
CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
    doctor_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'))
```

An overlapping active appointment is physically unstorable. This holds
regardless of how the service is written, how many instances run, or how
requests interleave. Violations surface as **409
`SLOT_NO_LONGER_AVAILABLE`**.

Cancelled and no-show rows are excluded from the constraint, which is
exactly what frees a slot again after a cancellation.

The availability check in the service is a courtesy that produces a good
error message in the common case. It is not the guarantee.

### 2. Deadlocks under contention

Correctness is not liveness. With several transactions inserting
overlapping rows at once, each holds index locks the others need, and
PostgreSQL breaks the cycle by killing one — reproducibly, at eight
concurrent bookings. "Deadlock detected" is not something to show a
patient.

Contenders therefore take a transaction-scoped advisory lock keyed on the
slot (`SlotLockRepository`), so they queue in a defined order. Bookings
for different times never block each other, and the lock is released with
the transaction.

### 3. Retries

A double-clicked button or a network retry must not create a second
appointment. Send an `Idempotency-Key` header: the key is recorded in the
same transaction as the appointment, so either both exist or neither
does, and a repeat replays the original response.

The stored fingerprint is a hash of the payload. The same key with a
*different* payload is a client bug, not a retry, and is rejected rather
than answered with someone else's booking.

### Meeting links and external failure

Creating a video meeting happens **after** the booking transaction
commits, in `MeetingAttachmentService`. A provider outage can therefore
never roll back a confirmed appointment or strand a slot. Failures are
logged; the appointment stands without a link, which is visible and
fixable.

**No meeting provider is currently configured.** Online appointments are
booked and confirmed with `meetingUrl: null` rather than with an invented
one — a fabricated `meet.google.com` URL is indistinguishable from a real
one until someone tries to join.

To add Google Meet: implement `MeetingProvider` against the Calendar API
(`events.insert` with `conferenceDataVersion=1`), register it as a bean,
and set the Google variables in `.env`. `NoopMeetingProvider` steps aside
automatically once another implementation exists.

---

## API

Swagger UI at `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at
`/v3/api-docs`.

### Public

```
GET  /api/v1/availability?from=2026-09-01&to=2026-09-30
GET  /api/v1/concern-categories
POST /api/v1/bookings              (optional header: Idempotency-Key)
POST /api/v1/contact
```

### Admin — all require `Authorization: Bearer <token>`

```
POST   /api/v1/admin/auth/login

GET    /api/v1/admin/dashboard
GET    /api/v1/admin/calendar?from=…&to=…

GET    /api/v1/admin/appointments        (paged, filterable)
GET    /api/v1/admin/appointments/{id}
PATCH  /api/v1/admin/appointments/{id}          {"status":"COMPLETED"|"NO_SHOW"}
PATCH  /api/v1/admin/appointments/{id}/cancel

GET    /api/v1/admin/availability/weekly
POST   /api/v1/admin/availability/weekly
PUT    /api/v1/admin/availability/weekly/{id}
DELETE /api/v1/admin/availability/weekly/{id}

GET    /api/v1/admin/blocked-periods
POST   /api/v1/admin/blocked-periods
PUT    /api/v1/admin/blocked-periods/{id}
DELETE /api/v1/admin/blocked-periods/{id}

GET    /api/v1/admin/patients            (paged, searchable)
GET    /api/v1/admin/patients/{id}
POST   /api/v1/admin/patients/{id}/notes
DELETE /api/v1/admin/patients/notes/{noteId}
```

### Errors

One shape everywhere:

```json
{
  "timestamp": "2026-09-01T20:00:00Z",
  "status": 409,
  "code": "SLOT_NO_LONGER_AVAILABLE",
  "message": "This appointment slot is no longer available.",
  "details": { "email": "Invalid email address." }
}
```

`code` is the stable contract; branch on it, not on `message`. Stack
traces and internal details are never returned.

---

## Security

- Admin endpoints require a signed, short-lived JWT. Everything under
  `/api/v1/admin` except `auth/login` is closed.
- Passwords are BCrypt at strength 12.
- Login gives the same reply for an unknown address and a wrong password
  — differing replies confirm which addresses exist.
- The token carries a subject and nothing else. A JWT is signed, not
  encrypted; anything inside it is readable by whoever holds it.
- The account is re-read on every request, so deactivating it takes
  effect immediately rather than at the next token expiry.
- CORS is an explicit allow-list, never `*`.
- Patient notes and personal data are never logged. Log lines record the
  fact of an event and its reference, not its contents.

### Cancelling, not deleting

Appointments are cancelled, never removed. The record is history someone
may need to see. Cancelling frees the slot immediately.

---

## Email

Off unless `MAIL_ENABLED=true`. A half-configured mail client that
silently drops messages is worse than none, because the practice would
believe patients had been told.

When on, booking sends a confirmation **to the address the patient
entered**, and cancelling or rescheduling from the admin sends the
matching notice. Each message is multipart — an HTML part styled like
the practice and a plain-text alternative — and the confirmation carries
an `.ics` calendar invitation.

Sending is `@Async` and failures are logged rather than thrown: the
appointment is real whether or not the email arrived, and failing a
booking because an SMTP server was down would be untrue.

### Configuring a provider

```bash
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com    # or Brevo, Postmark, Resend, SES…
MAIL_PORT=587
MAIL_USERNAME=…
MAIL_PASSWORD=…             # Gmail: an App Password, not the account password
MAIL_FROM=…
MAIL_REPLY_TO=…             # the body invites a reply; not a noreply address
```

Gmail rewrites `From` to the authenticated account regardless of what is
set, so `MAIL_FROM` should match `MAIL_USERNAME` there.

**Deliverability is not a code concern.** Whether a message lands in the
inbox or in spam depends on SPF and DKIM records on the sending domain,
configured at your DNS provider. A placeholder domain will not deliver
at all.

### Local development

Point at a capture server rather than a real provider:

```bash
mailpit --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025
```

```bash
MAIL_HOST=127.0.0.1
MAIL_PORT=1025
MAIL_AUTH=false
MAIL_STARTTLS=false
```

Mailpit accepts everything and delivers nothing, with a web inbox at
`http://localhost:8025`, so real addresses are never contacted during
development.

## Testing

```bash
mvn test
```

29 tests, all against a **real PostgreSQL** started in-process from a
bundled binary (Zonky). No Docker needed.

That choice matters: the scheduling guarantees rest on Postgres-specific
behaviour — GiST exclusion constraints, `tstzrange`, `btree_gist`,
advisory locks. An in-memory H2 would quietly skip the one thing most
worth testing.

| Suite | Covers |
| --- | --- |
| `ConcurrentBookingTest` | 8 threads, one slot, released together by a latch. Exactly one wins. |
| `AvailabilityServiceTest` | Working days, closed days, lunch gaps, blocked periods, booked slots, cancellation freeing a slot, lead time, horizon, slot length, timezone |
| `BookingServiceTest` | Online and on-site, double booking, idempotent retry, key reuse, past dates, lead time, outside hours, unknown category, returning patients |
| `SecurityTest` | Public endpoints open, every admin endpoint closed, forged tokens refused, login, account enumeration, error shape |
| `EmailNotificationTest` | Sent against a real in-process SMTP server (GreenMail): headers, both MIME parts, the calendar attachment, and that an online booking with no link never claims one is coming |

The concurrency test is the one to keep. It found the deadlock described
above, and it is the difference between believing double-booking is
impossible and knowing it.

---

## Architecture

Feature-oriented, not layer-oriented:

```
booking/       availability/   appointment/
patient/       contact/        admin/
security/      meeting/        notification/
common/        config/
```

- Controllers are thin: validate, delegate, return a DTO.
- Business rules live in services.
- Entities are never exposed through the API.
- Transactions sit on their own beans (`AppointmentCreationService`)
  rather than on private methods. Spring's transactions are proxy-based,
  so a call between two methods of the same class silently runs with no
  transaction at all — the worst way for a booking system to lose
  atomicity.

### Extension points

- `MeetingProvider` — video meetings.
- `NotificationService` — email. Two implementations: one that logs
  (the default) and one that sends over SMTP, selected by
  `MAIL_ENABLED`. See **Email** below.
- `PatientNote` — the seam for a future patient file. Deliberately
  minimal: enough that assessments and documents can be added without
  reshaping the module, but not a speculative medical-record system built
  before anyone has asked for one.

---

## Privacy

Designed to be privacy-conscious. That is not a compliance claim, and
nothing here should be read as one.

- The public booking form collects only what booking needs. There is no
  field for a diagnosis or medical history — an unauthenticated endpoint
  is the wrong place to invite clinical detail.
- Personal data never appears in logs or in URLs.
- Patient records are reachable only from the authenticated admin area.
- Identifiers are UUIDs, so records cannot be enumerated by counting.
