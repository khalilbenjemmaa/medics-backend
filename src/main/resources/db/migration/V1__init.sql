-- Exclusion constraints need GiST indexes over scalar equality
-- (doctor_id) alongside a range overlap, which core GiST cannot do.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------
-- doctor
-- One row, always. Appointments still carry an owner so the schema
-- does not have to be rewritten if the practice ever grows.
-- ---------------------------------------------------------------
CREATE TABLE doctor (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name  VARCHAR(80)  NOT NULL,
    last_name   VARCHAR(80)  NOT NULL,
    timezone    VARCHAR(64)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------
-- admin_user
-- The single operator account. Password is a hash, never a password.
-- ---------------------------------------------------------------
CREATE TABLE admin_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT admin_user_email_key UNIQUE (email)
);

-- ---------------------------------------------------------------
-- concern_category
-- Why someone is booking. Deliberately not a diagnosis list.
-- ---------------------------------------------------------------
CREATE TABLE concern_category (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    slug          VARCHAR(120) NOT NULL,
    description   TEXT,
    display_order INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT concern_category_slug_key UNIQUE (slug)
);

-- ---------------------------------------------------------------
-- patient
-- Only what booking genuinely needs. No medical detail is collected
-- through the public form.
-- ---------------------------------------------------------------
CREATE TABLE patient (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(40)  NOT NULL,
    date_of_birth DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- A returning patient books against the same record rather than
    -- accumulating a duplicate per appointment.
    CONSTRAINT patient_email_key UNIQUE (email)
);

CREATE INDEX idx_patient_phone      ON patient (phone);
CREATE INDEX idx_patient_last_name  ON patient (lower(last_name));

-- ---------------------------------------------------------------
-- weekly_availability
-- Recurring working hours in the doctor's local time. Stored as
-- LOCAL time, not an instant: "Monday 09:00" must stay 09:00 across
-- a daylight-saving change.
-- ---------------------------------------------------------------
CREATE TABLE weekly_availability (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id   UUID    NOT NULL REFERENCES doctor (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    start_time  TIME    NOT NULL,
    end_time    TIME    NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- ISO-8601: 1 = Monday .. 7 = Sunday.
    CONSTRAINT weekly_availability_dow_ck   CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT weekly_availability_range_ck CHECK (start_time < end_time),
    -- Multiple intervals per day are allowed; overlapping ones are not.
    CONSTRAINT weekly_availability_no_overlap EXCLUDE USING gist (
        doctor_id   WITH =,
        day_of_week WITH =,
        tsrange(
            ('2000-01-01'::date + start_time),
            ('2000-01-01'::date + end_time),
            '[)'
        ) WITH &&
    ) WHERE (active)
);

CREATE INDEX idx_weekly_availability_doctor ON weekly_availability (doctor_id, day_of_week);

-- ---------------------------------------------------------------
-- blocked_period
-- Holidays, closures, personal time. Absolute instants, because a
-- block is a real span of time rather than a recurring rule.
-- ---------------------------------------------------------------
CREATE TABLE blocked_period (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id  UUID        NOT NULL REFERENCES doctor (id) ON DELETE CASCADE,
    start_at   TIMESTAMPTZ NOT NULL,
    end_at     TIMESTAMPTZ NOT NULL,
    reason     VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT blocked_period_range_ck CHECK (start_at < end_at)
);

CREATE INDEX idx_blocked_period_window ON blocked_period (doctor_id, start_at, end_at);

-- ---------------------------------------------------------------
-- appointment
-- ---------------------------------------------------------------
CREATE TABLE appointment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference           VARCHAR(12) NOT NULL,
    doctor_id           UUID        NOT NULL REFERENCES doctor (id),
    patient_id          UUID        NOT NULL REFERENCES patient (id),
    concern_category_id UUID        NOT NULL REFERENCES concern_category (id),
    consultation_type   VARCHAR(16) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    meeting_url         VARCHAR(512),
    google_event_id     VARCHAR(255),
    patient_message     TEXT,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT appointment_reference_key UNIQUE (reference),
    CONSTRAINT appointment_range_ck CHECK (start_at < end_at),
    CONSTRAINT appointment_type_ck   CHECK (consultation_type IN ('ONLINE', 'ON_SITE')),
    CONSTRAINT appointment_status_ck CHECK (
        status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')
    ),

    -- THE double-booking guarantee.
    --
    -- Application-level "check then insert" always races: two requests
    -- can both read an empty slot before either writes. This constraint
    -- makes an overlapping active appointment physically impossible to
    -- store, so the guarantee holds no matter how the service is
    -- written, how many instances run, or how the calls interleave.
    --
    -- Cancelled and no-show appointments are excluded, which is what
    -- frees a slot again after a cancellation.
    CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
        doctor_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'))
);

CREATE INDEX idx_appointment_window  ON appointment (doctor_id, start_at, end_at);
CREATE INDEX idx_appointment_start   ON appointment (start_at);
CREATE INDEX idx_appointment_status  ON appointment (status);
CREATE INDEX idx_appointment_patient ON appointment (patient_id, start_at DESC);

-- ---------------------------------------------------------------
-- booking_idempotency
-- A double-clicked button or a network retry must not create a second
-- appointment. The key is unique, so the second attempt collides and
-- the original result is replayed.
-- ---------------------------------------------------------------
CREATE TABLE booking_idempotency (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    appointment_id UUID        NOT NULL REFERENCES appointment (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT booking_idempotency_key_uk UNIQUE (idempotency_key)
);

-- ---------------------------------------------------------------
-- patient_note
-- The seam for a future patient file. Admin-only, never public.
-- ---------------------------------------------------------------
CREATE TABLE patient_note (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    author     VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_note_patient ON patient_note (patient_id, created_at DESC);

-- ---------------------------------------------------------------
-- contact_request
-- ---------------------------------------------------------------
CREATE TABLE contact_request (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(80)  NOT NULL,
    last_name  VARCHAR(80)  NOT NULL,
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(40),
    message    TEXT         NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT contact_request_status_ck CHECK (status IN ('NEW', 'READ', 'ARCHIVED'))
);

CREATE INDEX idx_contact_request_status ON contact_request (status, created_at DESC);
