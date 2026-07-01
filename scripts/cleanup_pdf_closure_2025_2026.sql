-- Step ①: legacy PDF import used calendar_entries CLOSURE rows without real labels,
--          which painted normal PDF-white days (e.g. 28 May) as CLOSURE.
-- This schema has NO calendar_days table; use calendar_entries.
-- Academic year 2025-2026 overlap: 2025-09-01 .. 2026-08-31
--
-- If FK to calendar_entry_branches does not CASCADE, delete joins first:

DELETE ceb
FROM calendar_entry_branches ceb
INNER JOIN calendar_entries ce ON ce.id = ceb.calendar_entry_id
WHERE ce.entry_type = 'CLOSURE'
  AND ce.start_date <= '2026-08-31'
  AND ce.end_date >= '2025-09-01'
  AND COALESCE(TRIM(ce.label_en), '') IN ('', '—', '-')
  AND COALESCE(TRIM(ce.label_fr), '') IN ('', '—', '-')
  AND COALESCE(TRIM(ce.label_ki), '') IN ('', '—', '-');

DELETE FROM calendar_entries
WHERE entry_type = 'CLOSURE'
  AND start_date <= '2026-08-31'
  AND end_date >= '2025-09-01'
  AND COALESCE(TRIM(label_en), '') IN ('', '—', '-')
  AND COALESCE(TRIM(label_fr), '') IN ('', '—', '-')
  AND COALESCE(TRIM(label_ki), '') IN ('', '—', '-');
