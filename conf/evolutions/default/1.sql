# Likes schema
 
# --- !Ups

CREATE SEQUENCE movies_id_seq;
CREATE TABLE movies (
    id bigint NOT NULL DEFAULT nextval('movies_id_seq') PRIMARY KEY,
    title varchar(255) NOT NULL,
    releaseDate timestamp NOT NULL,
    tmdbId bigint NOT NULL
);

CREATE SEQUENCE likes_id_seq;
CREATE TABLE likes (
    id bigint NOT NULL DEFAULT nextval('likes_id_seq') PRIMARY KEY,
    movieId bigint NOT NULL,
    
 	FOREIGN KEY (movieId) REFERENCES movies(id) ON DELETE RESTRICT
);
 
# --- !Downs
 
DROP TABLE likes;
DROP SEQUENCE likes_id_seq;

DROP TABLE movies;
DROP SEQUENCE movies_id_seq;