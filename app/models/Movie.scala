package models
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Play.current
import java.util.Date

case class Movie(
  id: Long,
  title: String,
  releaseDate: Date,
  tmdbId: Long)

case class TmdbMovie(
  title: String,
  releaseDate: Date,
  tmdbId: Long)

object Movie {
  val movieParser = {
    get[Long]("id") ~
      get[String]("title") ~
      get[Date]("releaseDate") ~
      get[Long]("tmdbId") map {
        case id ~ title ~ releaseDate ~ tmdbId => Movie(id, title, releaseDate, tmdbId)
      }
  }

  def createOrUpdate(tmdbMovie: TmdbMovie): Movie = {
    DB.withConnection { implicit c =>

      val id = SQL("select id from movies where tmdbId = {tmdbId}").on(
        'tmdbId -> tmdbMovie.tmdbId).as(scalar[Long].singleOpt)

      if (id.isEmpty) {
        val id: Long = SQL("select next value for movies_id_seq").as(scalar[Long].single)
        SQL(
          """
          insert into movies values (
            {id}, {title}, {releaseDate}, {tmdbId}
          )
        """).on(
            'id -> id,
            'title -> tmdbMovie.title,
            'releaseDate -> tmdbMovie.releaseDate,
            'tmdbId -> tmdbMovie.tmdbId).executeUpdate()

        new Movie(id, tmdbMovie.title, tmdbMovie.releaseDate, tmdbMovie.tmdbId)
      } else {
        SQL("update movies set title={title} where id={id}").on(
          'title -> tmdbMovie.title,
          'id -> id.get).executeUpdate()
        SQL("update movies set releaseDate={releaseDate} where id={id}").on(
          'releaseDate -> tmdbMovie.releaseDate,
          'id -> id.get).executeUpdate()
        new Movie(id.get, tmdbMovie.title, tmdbMovie.releaseDate, tmdbMovie.tmdbId)
      }
    }
  }
}