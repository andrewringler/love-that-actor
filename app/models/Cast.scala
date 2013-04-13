package models
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Play.current
import java.util.Date

case class TmdbCast(id: Long, name: String, character: Option[String], order: Int, profilePath: Option[String])

case class Cast(id: Long, character: String, actor: Actor)

object Cast {
  val castParser = {
    get[Long]("castId") ~
      get[String]("character") ~
      get[Long]("actorId") ~
      get[String]("name") ~
      get[Long]("tmdbId") ~
      get[String]("profilePath") map {
        case castId ~ character ~ actorId ~ name ~ tmdbId ~ profilePath => Cast(castId, character, new Actor(actorId, name, tmdbId, profilePath))
      }
  }
  
  def all(movieId: Long): List[Cast] = DB.withConnection { implicit c =>
    SQL("""
        select c.id as castId, actorId, a.name, a.tmdbId, a.profilePath
        from cast c join actors a on c.actorId = a.id
        where c.movieId = {movieId}
        order by c.order asc
        """).on('movieId -> movieId)
        .as(castParser *)
  }

  def createOrUpdate(movieId: Long, cast: List[TmdbCast]): List[Cast] = {
     DB.withConnection { implicit c =>
      	SQL("delete from cast where movieid = {movieId}").on(
      		'movieId -> movieId).executeUpdate()
     }
    val movies = cast.filter(
        castMember => castMember.character.isDefined && castMember.profilePath.isDefined
     ).map({
      Cast.createOrUpdate(_)
    })
    movies
  }
  
  def createOrUpdate(tmdbCast: TmdbCast): Cast = {
    DB.withTransaction { implicit c =>
      		val actor = Actor.createOrUpdate(new TmdbActor(tmdbCast.name, tmdbCast.id, tmdbCast.profilePath))
      	    val id: Long = SQL("select next value for cast_id_seq").as(scalar[Long].single)

      		SQL("insert into cast (id, character, actorId, name, tmdbId, profilePath) values ({id}, {character},{actorId},{name},{tmdbId},{profilePath})").on(
      				'id -> id,
      				'character -> tmdbCast.character.get,
      				'actorId -> actor.id,
      				'name -> actor.name,
      				'tmdbId -> actor.tmdbId,
      				'profilePath -> actor.profilePath)
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