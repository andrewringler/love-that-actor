package models
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Play.current
import java.util.Date

case class Like(id: Long, movie: Movie)

object Like {
  val likeParser = {
    get[Long]("id") ~
      get[Long]("movieId") ~
      get[String]("title") ~
      get[Date]("releaseDate") ~
      get[Long]("tmdbId") map {
        case id ~ movieId ~ title ~ releaseDate ~ tmdbId => Like(id, new Movie(movieId, title, releaseDate, tmdbId))
      }
  }

  def all(): List[Like] = DB.withConnection { implicit c =>
    SQL("""
        select l.id as id, movieId, m.title, m.releaseDate, m.tmdbId
        from likes l join movies m on l.movieId = m.id
        order by m.title
        """).as(likeParser *)
  }

  def create(movieId: Long) {
    DB.withConnection { implicit c =>
      SQL("insert into likes (movieId) values ({movieId})").on(
        'movieId -> movieId).executeUpdate()
    }
  }

  def delete(id: Long) {
    DB.withConnection { implicit c =>
      SQL("delete from likes where id = {id}").on(
        'id -> id).executeUpdate()
    }
  }
}