# Working on this codebase

Scheduling backend for a single-practitioner occupational therapy
practice. Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway.

The README explains what the system does. This file is about how not to
break it. Everything below is either an invariant with a real failure
behind it, or a trap that has already cost time once.

---

## Running things

```bash
./dev.sh          # backend on :8080 — reads .env, checks Postgres first
./dev-db.sh       # pgweb database browser on :8081
mvn test          # 47 tests, real PostgreSQL in-process, no Docker
```

**Maven must be run from the project root.** Running it from a
subdirectory fails with "no POM in this directory" — easy to do after
`cd`-ing into a package.

**Java is not on the system PATH.** `dev.sh` sets `JAVA_HOME` to
`/opt/homebrew/opt/openjdk@21`. A bare `mvn` in a fresh shell fails with
"Unable to locate a Java Runtime"; export it first:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

If a build reports "class file not found" for a class that plainly
exists, it is stale incremental state. `mvn clean test`.

---

## Invariants — do not break these

### 1. Double booking is prevented by the database, not by code

`appointment_no_overlap` in `V1__init.sql` is a PostgreSQL exclusion
constraint over `tstzrange`. It is the only thing that actually
guarantees two people cannot hold the same slot.

Application-level "check then insert" always races: two requests can
both read a slot as free before either writes. The availability check
in `AppointmentCreationService` exists to produce a good error message
in the common case — it is **not** the guarantee, and the comments say
so. Do not remove the constraint, weaken it, or "simplify" the service
by trusting the pre-check.

### 2. `AppointmentStatus.BLOCKING` mirrors that constraint's WHERE clause

The constraint applies `WHERE status IN ('PENDING','CONFIRMED','COMPLETED')`.
`AppointmentStatus.BLOCKING` lists the same three. **Change one, change
the other.** If they drift, availability will offer a slot the database
then refuses to store, and the failure appears as a mysterious 409 for
one specific status.

### 3. Never set `hibernate.jdbc.time_zone`

It looks harmless and is not. It also shifts `TIME` columns, which
silently moved the whole working day by an hour in summer — a 09:00
interval read back as 11:00. It is redundant for `TIMESTAMPTZ` anyway.
There is a test covering this.

### 4. Weekly hours are local wall-clock; everything else is an Instant

`weekly_availability` stores `TIME` + day-of-week so "Monday at nine"
survives a daylight-saving change. Appointments, blocked periods and
audit entries are `Instant` in `TIMESTAMPTZ`. Do not "unify" these.

### 5. `@Transactional` never goes on a self-invoked method

Spring's transactions are proxy-based, so a call from one method of a
class to another bypasses the proxy and runs with **no transaction at
all**, silently. This is why `AppointmentCreationService` and
`MeetingAttachmentService` are separate beans rather than private
methods on `BookingService`. Keep that shape.

### 6. Nothing slow inside a booking transaction

No HTTP, no mail, no calendar API. Meeting creation and notifications
run *after* commit, so an external outage can never roll back a
confirmed appointment or strand a slot. If you add an integration,
follow that pattern.

### 7. Never `(:param is null or column = :param)` in JPQL

PostgreSQL cannot infer the type of a bare NULL that appears only in an
`IS NULL` test. This produced a 500 on the plainest admin request
("could not determine data type of parameter $2"). Use Specifications —
see `AppointmentSpecifications`. An absent filter must contribute no SQL.

### 8. Entities not extending `BaseEntity` need `@EntityListeners`

`@CreatedDate` is populated by `AuditingEntityListener`, which
`BaseEntity` declares. `AuditEntry` and `BookingIdempotency` carry their
own ids, so they declare it themselves. Without it `created_at` inserts
as NULL and violates the constraint.

### 9. Never fabricate a meeting URL

`meetingUrl` is null until a real provider supplies one. A plausible
`meet.google.com` link is indistinguishable from a real one until
someone tries to join. `NoopMeetingProvider` returns empty on purpose.

### 10. Never log personal data

Log the appointment reference, never the patient's name, email, message
or note content. A log file is a copy of whatever you put in it, kept
longer and read by more people than the record itself.

### 11. Flyway owns the schema

`ddl-auto=validate`. Add `V5__…sql`; never edit an applied migration.

---

## Testing

Tests run against a **real PostgreSQL** started in-process (Zonky), not
H2. This is deliberate: the guarantees rest on Postgres-specific
behaviour — GiST exclusion constraints, `tstzrange`, `btree_gist`,
advisory locks — that H2 would quietly fake. Do not "simplify" to H2.

`ConcurrentBookingTest` is the one to keep working. It releases eight
threads onto one slot and asserts exactly one wins. It found the
deadlock that advisory locking now prevents.

Email is tested against a real in-process SMTP server (GreenMail), not a
mocked sender. A mock proves `send()` was called; it does not prove a
well-formed message came out. That distinction caught a bug where every
online booking silently sent nothing.

**Write the failing test first when fixing a bug here.** Two production
bugs reached this codebase because a test asserted the endpoint required
auth and never actually called it with real arguments.

---

## Conventions

- Feature packages (`booking/`, `availability/`, `patient/`…), not layers.
- Controllers are thin: validate, delegate, return a DTO.
- **Entities are never returned from a controller.**
- Errors go through `ApiException` with a stable machine-readable `code`;
  the frontend branches on `code`, not on `message`. Never leak stack
  traces or internal detail.
- UUIDs for anything appearing in a URL, so records cannot be enumerated.

## Deliberately absent — do not add

- **No CMS.** Site copy, imagery and credentials live in the frontend as
  static content. No endpoint should return a biography.
- **No doctor-selection endpoint.** One practitioner; a patient is never
  asked to choose. `DoctorProvider` is the only place that assumption
  lives.
- **No patient-facing endpoint for notes or clinical data.**

## Email

Off unless `MAIL_ENABLED=true`. A half-configured mail client that
silently drops messages is worse than none, because the practice would
believe patients had been told.

Deliverability is a reputation problem, not a code problem. See the
README before attempting to fix spam placement by editing templates.

## Secrets

`.env` is gitignored and must stay that way. `.env.example` carries
empty placeholders only. Never commit credentials, and never echo them
into terminal output.
