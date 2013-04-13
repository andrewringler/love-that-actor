package models
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Play.current
import java.util.Date
import play.api.cache.Cache
import play.api.libs.ws.WS
import play.api.libs.json._
import play.api.data.validation.ValidationError
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.Logger

case class Actor(
  id: Long,
  name: String,
  tmdbId: Long,
  profilePath: String)

case class TmdbActor(
  name: String,
  tmdbId: Long,
  profilePath: Option[String])

object Actor {
  val actorParser = {
    get[Long]("id") ~
      get[String]("name") ~
      get[Long]("tmdbId") ~
      get[String]("profilePath") map {
        case id ~ name ~ tmdbId ~ profilePath => Actor(id, name, tmdbId, profilePath)
      }
  }

  def getActor(id: Long): Actor = DB.withConnection { implicit c =>
    SQL("""
        select a.id as id, a.name, a.tmdbId, a.profilePath
        from actors a where id = {id}
        """).on('id -> id)
      .as(actorParser.single)
  }

  def getActorByTmdbId(tmdbId: Long): Actor = DB.withConnection { implicit c =>
    SQL("""
        select a.id as id, a.name, a.tmdbId, a.profilePath
        from actors a where tmdbId = {tmdbId}
        """).on('tmdbId -> tmdbId)
      .as(actorParser.single)
  }

  def createOrUpdate(tmdbActor: TmdbActor): Actor = {
    DB.withTransaction { implicit c =>
      val id = SQL("select id from actors where tmdbId = {tmdbId}").on(
        'tmdbId -> tmdbActor.tmdbId).as(scalar[Long].singleOpt)

      if (id.isEmpty) {
        val id: Long = SQL("select next value for actors_id_seq").as(scalar[Long].single)
        SQL(
          """
          insert into actors values (
            {id}, {name}, {tmdbId}, {profilePath}
          )
        """).on(
            'id -> id,
            'name -> tmdbActor.name,
            'tmdbId -> tmdbActor.tmdbId,
            'profilePath -> tmdbActor.profilePath).executeUpdate()

        new Actor(id, tmdbActor.name, tmdbActor.tmdbId, tmdbActor.profilePath.get)
      } else {
        SQL("update actors set name={name} where id={id}").on(
          'title -> tmdbActor.name,
          'id -> id.get).executeUpdate()
        SQL("update actors set profilePath={profilePath} where id={id}").on(
          'profilePath -> tmdbActor.profilePath,
          'id -> id.get).executeUpdate()
        new Actor(id.get, tmdbActor.name, tmdbActor.tmdbId, tmdbActor.profilePath.get)
      }
    }
  }
}