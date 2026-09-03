-- Development / bootstrap seed.
--
-- Exactly one doctor, a working week, and the concern categories the
-- booking flow offers. The categories mirror the areas of support the
-- public site already describes, so the two stay in step.
--
-- No patients and no appointments are seeded: fabricated people in a
-- clinical system are indistinguishable from real ones once the data
-- has been in use for a week.

INSERT INTO doctor (id, first_name, last_name, timezone, active)
VALUES ('00000000-0000-0000-0000-000000000001', 'Reem', 'Amiri', 'Europe/Paris', TRUE);

-- Mon-Thu split day, Saturday morning only, Friday and Sunday closed.
INSERT INTO weekly_availability (doctor_id, day_of_week, start_time, end_time) VALUES
    ('00000000-0000-0000-0000-000000000001', 1, '09:00', '12:00'),
    ('00000000-0000-0000-0000-000000000001', 1, '14:00', '18:00'),
    ('00000000-0000-0000-0000-000000000001', 2, '09:00', '12:00'),
    ('00000000-0000-0000-0000-000000000001', 2, '14:00', '18:00'),
    ('00000000-0000-0000-0000-000000000001', 3, '09:00', '12:00'),
    ('00000000-0000-0000-0000-000000000001', 3, '14:00', '18:00'),
    ('00000000-0000-0000-0000-000000000001', 4, '09:00', '12:00'),
    ('00000000-0000-0000-0000-000000000001', 4, '14:00', '17:00'),
    ('00000000-0000-0000-0000-000000000001', 6, '09:00', '13:00');

-- Placeholder categories. They describe what someone finds difficult,
-- not a condition the practice claims to treat, and are meant to be
-- replaced with the practitioner's own wording.
INSERT INTO concern_category (name, slug, description, display_order) VALUES
    ('Sensory integration', 'sensory-integration',
     'Difficulty organising everyday sensory information — movement, sound, touch or balance.', 1),
    ('Occupational therapy assessment', 'occupational-therapy-assessment',
     'A first appointment to work out where the difficulty actually sits.', 2),
    ('Child development', 'child-development',
     'Concerns about how a child is developing everyday skills.', 3),
    ('Daily-life independence', 'daily-life-independence',
     'Support with the ordinary tasks a day is made of.', 4),
    ('Motor skills', 'motor-skills',
     'Difficulty with coordination, handwriting, dressing or other fine and gross motor tasks.', 5),
    ('Sensory environment support', 'sensory-environment-support',
     'Advice on setting up a space at home, in a classroom or in a care setting.', 6),
    ('Something else', 'other',
     'Not sure which of these fits. Say so when booking and we will work it out together.', 99);
