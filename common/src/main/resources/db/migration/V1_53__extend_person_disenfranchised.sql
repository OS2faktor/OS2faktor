ALTER TABLE persons ADD COLUMN locked_disenfranchised BOOLEAN NOT NULL DEFAULT 0 AFTER locked_dead;
ALTER TABLE persons_aud ADD COLUMN locked_disenfranchised BOOLEAN NOT NULL DEFAULT 0 AFTER locked_dead;