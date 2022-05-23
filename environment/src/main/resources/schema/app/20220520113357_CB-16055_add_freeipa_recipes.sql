-- // CB-16699 Bring your own DNS zone - Rename endpoint body field, step 2
-- Migration SQL that makes the change goes here.
CREATE SEQUENCE IF NOT EXISTS environment_freeiparecipes_id_seq START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS environment_freeiparecipes
(
    id                  BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('environment_freeiparecipes_id_seq'),
    environment_id      BIGINT NOT NULL,
    recipe              CHARACTER VARYING (255)
);


-- //@UNDO
-- SQL to undo the change goes here.

DROP TABLE environment_freeiparecipes;

DROP SEQUENCE environment_freeiparecipes_id_seq;