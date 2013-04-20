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
      get[Long]("tmdbId") ~
      get[String]("posterPath") map {
        case id ~ movieId ~ title ~ releaseDate ~ tmdbId ~ posterPath => Like(id, new Movie(movieId, title, releaseDate, tmdbId, posterPath, Nil))
      }
  }

  def all(): List[Like] = DB.withConnection { implicit c =>
    val likes = SQL("""
        select l.id as id, movieId, m.title, m.releaseDate, m.tmdbId, m.posterPath
        from likes l join movies m on l.movieId = m.id
        order by m.releaseDate desc, m.title asc
        """).as(likeParser *)

    likes.map {
      case like =>
        val cast = Cast.all(like.movie.id) 
        new Like(like.id, new Movie(like.movie.id, like.movie.title, like.movie.releaseDate, like.movie.tmdbId, like.movie.posterPath, cast))
    }
  }

  def create(movieId: Long) {
    DB.withTransaction { implicit c =>
      val id = SQL("select id from likes where id = {id}").on(
        'id -> movieId).as(scalar[Long].singleOpt)

      if (id.isEmpty) {
        SQL("insert into likes (movieId) values ({movieId})").on(
          'movieId -> movieId).executeUpdate()
      }
    }
  }

  def delete(id: Long) {
    DB.withConnection { implicit c =>
      SQL("delete from likes where id = {id}").on(
        'id -> id).executeUpdate()
    }
  }
}