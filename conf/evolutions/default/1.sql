# Likes schema
 
# --- !Ups

CREATE SEQUENCE likes_id_seq;
CREATE TABLE likes (
    id integer NOT NULL DEFAULT nextval('likes_id_seq'),
    label varchar(255)
);
 
# --- !Downs
 
DROP TABLE likes;
DROP SEQUENCE likes_id_seq;