# Likes schema
 
# --- !Ups

CREATE SEQUENCE actors_id_seq;
CREATE TABLE actors (
	id bigint NOT NULL DEFAULT nextval('actors_id_seq') PRIMARY KEY,
	name varchar(255) NOT NULL,
	tmdbId bigint NOT NULL UNIQUE,
	profilePath varchar(255) NOT NULL
);

CREATE SEQUENCE cast_id_seq;
CREATE TABLE cast (
	id bigint NOT NULL DEFAULT nextval('cast_id_seq') PRIMARY KEY,
	character varchar(1024) NOT NULL,
	actorId bigint NOT NULL,
	movieId bigint NOT NULL,
		
	FOREIGN KEY (actorId) REFERENCES actors(id) ON DELETE RESTRICT,
);

CREATE SEQUENCE movies_id_seq;
CREATE TABLE movies (
    id bigint NOT NULL DEFAULT nextval('movies_id_seq') PRIMARY KEY,
    title varchar(255) NOT NULL,
    releaseDate timestamp NOT NULL,
    tmdbId bigint NOT NULL UNIQUE,
    posterPath varchar(255) NOT NULL
);

CREATE SEQUENCE likes_id_seq;
CREATE TABLE likes (
    id bigint NOT NULL DEFAULT nextval('likes_id_seq') PRIMARY KEY,
    movieId bigint NOT NULL,
    
 	FOREIGN KEY (movieId) REFERENCES movies(id) ON DELETE RESTRICT
);
 
ALTER TABLE cast ADD FOREIGN KEY (movieId) REFERENCES movies(id) ON DELETE RESTRICT;
 
 
 
# --- !Downs
 
DROP TABLE cast;
DROP SEQUENCE cast_id_seq;

DROP TABLE actors;
DROP SEQUENCE actors_id_seq;

DROP TABLE likes;
DROP SEQUENCE likes_id_seq;

DROP TABLE movies;
DROP SEQUENCE movies_id_seq;