package models

import anorm._
import anorm.SqlParser._
import play.api.Play.current
import play.api.db._

case class TmdbCast(id: Long, name: String, character: Option[String], order: Int, profilePath: Option[String])

case class Cast(id: Long, character: String, actor: Actor)

object Cast {
  val castParser = {
    get[Long]("id") ~
      get[String]("characterName") ~
      get[Long]("id") ~
      get[String]("name") ~
      get[Long]("tmdbId") ~
      get[String]("profilePath") map {
        case castId ~ characterName ~ actorId ~ name ~ tmdbId ~ profilePath => Cast(castId, characterName, new Actor(actorId, name, tmdbId, profilePath))
      }
  }

  def all(movieId: Long): List[Cast] = DB.withConnection { implicit c =>
    /* NOTE I would have love to alias the columns, c.id as castId
     * and a.id as actorId, but the postresql & h2 drivers drop these names over the wire
     * https://groups.google.com/forum/?fromgroups=#!topic/play-framework/YfLF89ztFKQ
     */
    SQL("""
        select c.id, c.characterName, a.id, a.name, a.tmdbId, a.profilePath
    	from cast c, actors a
    	where c.movieId = {movieId} and c.actorId = a.id
    	order by castOrder
        """).on('movieId -> movieId)
      .as(castParser *)
  }

  def createOrUpdate(movieId: Long, tmdbCast: List[TmdbCast]): List[Cast] = {
    DB.withConnection { implicit c =>
      SQL("delete from cast where movieid = {movieId}").on(
        'movieId -> movieId).executeUpdate()
    }
    val cast = tmdbCast.filter(
      castMember => castMember.character.isDefined && castMember.profilePath.isDefined).map({
        Cast.createOrUpdate(movieId, _)
      })
    cast
  }

  def createOrUpdate(movieId: Long, tmdbCast: TmdbCast): Cast = {
    val actor = Actor.createOrUpdate(new TmdbActor(tmdbCast.name, tmdbCast.id, tmdbCast.profilePath))
    DB.withConnection { implicit c =>
      val id: Long = SQL("select next value for cast_id_seq").as(scalar[Long].single)

      // TODO character actually contains multiple fields like:
      // Gary Johnston (voice) / Joe (voice) / Kim Jong Il (voice) / ...
      SQL("insert into cast (id, characterName, castOrder, actorId, movieId) values ({id}, {characterName},{castOrder},{actorId},{movieId})").on(
        'id -> id,
        'characterName -> tmdbCast.character.get,
        'castOrder -> tmdbCast.order,
        'actorId -> actor.id,
        'movieId -> movieId)
        .executeUpdate()

      new Cast(id, tmdbCast.character.get, actor)
    }
  }

  def delete(id: Long) {
    DB.withConnection { implicit c =>
      SQL("delete from likes where id = {id}").on(
        'id -> id).executeUpdate()
    }
  }
}