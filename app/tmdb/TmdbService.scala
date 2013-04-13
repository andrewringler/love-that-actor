package tmdb

import java.util.Date

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader

import models.Movie
import models.TmdbCast
import models.TmdbMovie
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.__
import play.api.libs.ws.WS
import scala.concurrent._
import ExecutionContext.Implicits.global

object TmdbService {
  case class Search(
    total: Int = 0,
    movies: List[TmdbMovie] = Nil)

  val ensureOneTmdbRequestPerSearchTerm =
    CacheBuilder.newBuilder()
      .expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES)
      .build(new CacheLoader[String, scala.concurrent.Future[List[Movie]]]() {
        override def load(searchQuery: String): scala.concurrent.Future[List[Movie]] = {
          tmdbMovieSearchWebServiceCall(searchQuery)
        }
      })
  def tmdbMovieSearch(s: String): scala.concurrent.Future[List[Movie]] = {
    ensureOneTmdbRequestPerSearchTerm.get(s)
  }

  def tmdbMovieSearchWebServiceCall(s: String): scala.concurrent.Future[List[Movie]] = {
    val cachedSearch = Cache.getAs[Search]("search." + s)
    if (cachedSearch.isDefined) {
      future { toMovies(s, cachedSearch.get.movies) }
    } else {
      WS.url("http://api.themoviedb.org/3/search/movie")
        .withQueryString(("query", s), ("api_key", Global.tmdbApiKey))
        .get().map { response =>
          implicit val castReads = (
            (__ \ "id").read[Long] ~
            (__ \ "name").read[String] ~
            (__ \ "character").readNullable[String] ~
            (__ \ "order").read[Int] ~
            (__ \ "profile_path").readNullable[String])(TmdbCast)
          implicit val movieReads = (
            (__ \ "title").read[String] ~
            (__ \ "release_date").readNullable[Date] ~
            (__ \ "id").read[Long] ~
            (__ \ "poster_path").readNullable[String] ~
            (__ \ "casts" \ "cast").readNullable[List[TmdbCast]].orElse((__ \ "casts").readNullable[List[TmdbCast]]))(TmdbMovie)
          implicit val searchReads = (
            (__ \ "total_results").read[Int] ~
            (__ \ "results").read[List[TmdbMovie]])(Search)

          response.json.validate[Search].fold(
            valid = (search => {
              Cache.set("search." + s, search, 60 * 60)
              toMovies(s, search.movies)
            }),
            invalid = (e => {
              Logger.debug(response.status + " (GET) " + response.body)
              Logger.error("Invalid JSON during query '+s+'" + e.toString)
              Nil
            }))
        }
    }
  }

  def toMovies(s: String, tmdbMovies: List[TmdbMovie]) = {
    tmdbMovies.filter(
      movie => movie.releaseDate.isDefined && movie.posterPath.isDefined).map({
        Movie.createOrUpdate(_)
      })
  }
}