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
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader

case class Movie(
  id: Long,
  title: String,
  releaseDate: Date,
  tmdbId: Long,
  posterPath: String,
  cast: List[Cast]) {
  def abbrTitle() = {
    title.substring(0, 20)
  }
  def needsUpdate = {
    // TODO check age of record
    !cast.isEmpty
  }
}

case class TmdbMovie(
  title: String,
  releaseDate: Option[Date],
  tmdbId: Long,
  posterPath: Option[String],
  cast: Option[List[TmdbCast]])

object Movie {
  val movieParser = {
    get[Long]("id") ~
      get[String]("title") ~
      get[Date]("releaseDate") ~
      get[Long]("tmdbId") ~
      get[String]("posterPath") map {
        case id ~ title ~ releaseDate ~ tmdbId ~ posterPath => Movie(id, title, releaseDate, tmdbId, posterPath, Nil)
      }
  }

  def getMovie(id: Long): Movie = DB.withConnection { implicit c =>
    val partialMovie = SQL("""
        select m.id as id, m.title, m.releaseDate, m.tmdbId, m.posterPath
        from movies m where id = {id}
        """).on('id -> id)
      .as(movieParser.single)

    val cast = SQL("""
         select c.id as castId, c.characterName, a.id as actorId, a.name, a.tmdbId, a.profilePath
         from cast c join actors a on c.actorId = a.id
         where c.movieId = {movieId}
         """).on('movieId -> id)
      .as(Cast.castParser *)

    new Movie(partialMovie.id, partialMovie.title, partialMovie.releaseDate, partialMovie.tmdbId, partialMovie.posterPath, cast)
  }

  /* Ensure we only create new movies if we in fact don't already
   * have a mapping for a particular Tmdb ID
   * this is sort of an identity map, although we only keep around
   * really recent records
   */
  case class TmdbMovieEqualityByTmdbId(tmdbId: Long)(val tmdbMovie: TmdbMovie)
  val partialIdentityMap =
    CacheBuilder.newBuilder()
      .expireAfterAccess(5, java.util.concurrent.TimeUnit.SECONDS)
      .build(new CacheLoader[TmdbMovieEqualityByTmdbId, Movie]() {
        override def load(tmdbMovieEqualityByTmdbId: TmdbMovieEqualityByTmdbId): Movie = {
          createOrUpdateInt(tmdbMovieEqualityByTmdbId.tmdbMovie)
        }
      })
  def createOrUpdate(tmdbMovie: TmdbMovie): Movie = {
    partialIdentityMap.get(TmdbMovieEqualityByTmdbId(tmdbMovie.tmdbId)(tmdbMovie))
  }
  private def createOrUpdateInt(tmdbMovie: TmdbMovie): Movie = {
    DB.withTransaction { implicit c =>
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
            'posterPath -> tmdbMovie.posterPath).executeUpdate()

        //        val cast = Cast.createOrUpdate(id, tmdbMovie.cast.getOrElse(Nil))
        new Movie(id, tmdbMovie.title, tmdbMovie.releaseDate.get, tmdbMovie.tmdbId, tmdbMovie.posterPath.get, Nil)
      } else {
        SQL("update movies set title={title} where id={id}").on(
          'title -> tmdbMovie.title,
          'id -> id.get).executeUpdate()
        SQL("update movies set releaseDate={releaseDate} where id={id}").on(
          'releaseDate -> tmdbMovie.releaseDate,
          'id -> id.get).executeUpdate()
        SQL("update movies set posterPath={posterPath} where id={id}").on(
          'posterPath -> tmdbMovie.posterPath,
          'id -> id.get).executeUpdate()

        //        val cast = Cast.createOrUpdate(id.get, tmdbMovie.cast.getOrElse(Nil))
        new Movie(id.get, tmdbMovie.title, tmdbMovie.releaseDate.get, tmdbMovie.tmdbId, tmdbMovie.posterPath.get, Nil)
      }
    }
  }
  def update(id: Long, tmdbMovie: TmdbMovie): Boolean = {
    DB.withTransaction { implicit c =>
      SQL("update movies set title={title} where id={id}").on(
        'title -> tmdbMovie.title,
        'id -> id).executeUpdate()
      SQL("update movies set releaseDate={releaseDate} where id={id}").on(
        'releaseDate -> tmdbMovie.releaseDate,
        'id -> id).executeUpdate()
      SQL("update movies set posterPath={posterPath} where id={id}").on(
        'posterPath -> tmdbMovie.posterPath,
        'id -> id).executeUpdate()

    }
    Cast.createOrUpdate(id, tmdbMovie.cast.get)
    true
  }
}