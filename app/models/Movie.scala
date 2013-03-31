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

case class Movie(
  id: Long,
  title: String,
  releaseDate: Date,
  tmdbId: Long,
  posterPath: String)

case class TmdbMovie(
  title: String,
  releaseDate: Option[Date],
  tmdbId: Long,
  posterPath: Option[String])

object Movie {
  lazy val tmdbConfigSlow: TmdbConfig = tmdbGetConfigSlow
  case class TmdbConfig(baseUrl: String, secureBaseUrl: String)
  def tmdbGetConfigSlow(): TmdbConfig = {
    Cache.getOrElse[TmdbConfig]("tmdb.config") {
      val result = WS.url("http://api.themoviedb.org/3/configuration")
        .withQueryString(("api_key", Global.tmdbApiKey))
        .get().map { response =>
          implicit val tmdbConfigReads: Reads[TmdbConfig] = (
            (__ \ "images" \ "base_url").read[String] ~
            (__ \ "images" \ "secure_base_url").read[String])(TmdbConfig)
          //        Logger.debug(response.status + " (GET) " + response.body)
          response.json.validate[TmdbConfig].fold(
            valid = (config =>
              config),
            invalid = (e => {
              Logger.error("Invalid JSON " + e.toString)
              // TODO handle
              throw new RuntimeException
            }))
        }
      import scala.concurrent._
      import scala.concurrent.duration._
      Await.result(result, 15 seconds)
    }
  }
  
  val movieParser = {
    get[Long]("id") ~
      get[String]("title") ~
      get[Date]("releaseDate") ~
      get[Long]("tmdbId") ~
      get[String]("posterPath") map {
        case id ~ title ~ releaseDate ~ tmdbId ~ posterPath => Movie(id, title, releaseDate, tmdbId, posterPath)
      }
  }

  def createOrUpdate(tmdbMovie: TmdbMovie): Movie = {
    DB.withConnection { implicit c =>
      // TODO pull poster size from config
      val posterPath = tmdbConfigSlow.baseUrl + "w342" + tmdbMovie.posterPath.get
      val id = SQL("select id from movies where tmdbId = {tmdbId}").on(
        'tmdbId -> tmdbMovie.tmdbId).as(scalar[Long].singleOpt)

      if (id.isEmpty) {
        val id: Long = SQL("select next value for movies_id_seq").as(scalar[Long].single)
        SQL(
          """
          insert into movies values (
            {id}, {title}, {releaseDate}, {tmdbId}, {posterPath}
          )
        """).on(
            'id -> id,
            'title -> tmdbMovie.title,
            'releaseDate -> tmdbMovie.releaseDate,
            'tmdbId -> tmdbMovie.tmdbId,
            'posterPath -> posterPath
            ).executeUpdate()

        new Movie(id, tmdbMovie.title, tmdbMovie.releaseDate.get, tmdbMovie.tmdbId, posterPath)
      } else {
        SQL("update movies set title={title} where id={id}").on(
          'title -> tmdbMovie.title,
          'id -> id.get).executeUpdate()
        SQL("update movies set releaseDate={releaseDate} where id={id}").on(
          'releaseDate -> tmdbMovie.releaseDate,
          'id -> id.get).executeUpdate()
        new Movie(id.get, tmdbMovie.title, tmdbMovie.releaseDate.get, tmdbMovie.tmdbId, posterPath)
      }
    }
  }
}