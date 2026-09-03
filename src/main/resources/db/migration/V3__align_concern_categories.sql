-- Align the booking reasons with the areas of support the public site
-- already describes.
--
-- The two lists had drifted: the site offered "Developmental support"
-- and "Functional difficulties" while the database offered "Child
-- development" and "Motor skills". The frontend maps a chosen area to a
-- category by slug, so a mismatch means a booking that cannot be
-- submitted at all.
--
-- The site's wording wins. It is the copy the practitioner reviewed and
-- the words a patient actually reads before choosing.

UPDATE concern_category
SET slug = 'developmental-support',
    name = 'Developmental support',
    description = 'Concerns about how everyday skills are developing.'
WHERE slug = 'child-development';

UPDATE concern_category
SET slug = 'functional-difficulties',
    name = 'Functional difficulties',
    description = 'Everyday tasks that are harder than they should be.'
WHERE slug = 'motor-skills';

UPDATE concern_category
SET slug = 'personalised-therapy',
    name = 'Personalised therapy',
    description = 'A plan built around one goal that matters to you.'
WHERE slug = 'other';

-- Display order follows the site's own ordering.
UPDATE concern_category SET display_order = 1 WHERE slug = 'sensory-integration';
UPDATE concern_category SET display_order = 2 WHERE slug = 'occupational-therapy-assessment';
UPDATE concern_category SET display_order = 3 WHERE slug = 'developmental-support';
UPDATE concern_category SET display_order = 4 WHERE slug = 'daily-life-independence';
UPDATE concern_category SET display_order = 5 WHERE slug = 'functional-difficulties';
UPDATE concern_category SET display_order = 6 WHERE slug = 'sensory-environment-support';
UPDATE concern_category SET display_order = 7 WHERE slug = 'personalised-therapy';
