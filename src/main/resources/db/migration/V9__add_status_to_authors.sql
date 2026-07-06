-- GH-117: autorzy dostają status moderacji (symetrycznie do publishers) — tworzeni w locie przez
-- użytkowników jako PENDING, przez admina jako APPROVED. Dotychczasowe wpisy uznajemy za APPROVED.
ALTER TABLE authors ADD COLUMN status VARCHAR(20);
UPDATE authors SET status = 'APPROVED';
ALTER TABLE authors ALTER COLUMN status SET NOT NULL;