-- ---------------------------------------------------------------
-- audit_log
--
-- Who changed what, and when. Cancelling and rescheduling affect real
-- people's plans, so "the appointment moved and nobody knows why" is
-- not an acceptable state for this system to reach.
--
-- Deliberately append-only in practice: there is no update or delete
-- path in the application. The `detail` column holds a short factual
-- summary, never patient contact details or note content — an audit
-- trail is read by more people than the record it describes.
-- ---------------------------------------------------------------
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor       VARCHAR(255) NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(64)  NOT NULL,
    entity_id   UUID,
    -- Human-readable reference, kept so the entry still means something
    -- after the row it points at is gone.
    entity_ref  VARCHAR(64),
    detail      VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_entity  ON audit_log (entity_type, entity_id);

-- Contact requests gain a handled-at stamp so the inbox can show what
-- has actually been dealt with rather than only what has been opened.
ALTER TABLE contact_request ADD COLUMN handled_at TIMESTAMPTZ;

-- Appointments record how they were created. A booking taken over the
-- phone by the practitioner is a different thing from one a patient
-- made themselves, and the difference matters when reading a list.
ALTER TABLE appointment
    ADD COLUMN created_by VARCHAR(16) NOT NULL DEFAULT 'PATIENT';

ALTER TABLE appointment
    ADD CONSTRAINT appointment_created_by_ck
    CHECK (created_by IN ('PATIENT', 'ADMIN'));
