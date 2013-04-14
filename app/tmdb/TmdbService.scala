package tmdb

import java.util.Date
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.future
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import models.Movie
import models.TmdbCast
import models.TmdbMovie
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.__
import play.api.libs.ws.WS
import play.api.http.Status

object TmdbService {
  // Search
  def tmdbMovieSearch(s: String): scala.concurrent.Future[List[Movie]] = {
    ensureOneTmdbRequestPerSearchTerm.get(s.toLowerCase())
  }

  private case class Search(
    total: Int = 0,
    movies: List[TmdbMovie] = Nil)

  private val ensureOneTmdbRequestPerSearchTerm =
    CacheBuilder.newBuilder()
      .expireAfterAccess(5, java.util.concurrent.TimeUnit.SECONDS)
      .build(new CacheLoader[String, scala.concurrent.Future[List[Movie]]]() {
        override def load(searchQuery: String): scala.concurrent.Future[List[Movie]] = {
          tmdbMovieSearchWebServiceCall(searchQuery)
        }
      })

  private def tmdbMovieSearchWebServiceCall(s: String): scala.concurrent.Future[List[Movie]] = {
    val cacheKey = "search." + s
    val cachedSearch = Cache.getAs[Search](cacheKey)
    if (cachedSearch.isDefined) {
      Logger.debug("CACHE HIT '" + cacheKey + "'")
      future { toMovies(s, cachedSearch.get.movies) }
    } else {
      WS.url("http://api.themoviedb.org/3/search/movie")
        .withQueryString(("query", s), ("api_key", Global.tmdbApiKey))
        .get().map { response =>
          if (response.status == Status.OK) {
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
                val tmdbSearchCacheTimeInSeconds = 60 * 60 //1-hour
                Cache.set(cacheKey, search, tmdbSearchCacheTimeInSeconds)
                val movies = toMovies(s, search.movies)
                movies foreach {
                  case movie =>
                    Akka.future {
                      val fetchedMovieInfoFuture = fetchAndPopulateFullMovieInfo(movie)
                      fetchedMovieInfoFuture onFailure {
                        case e: Exception => Logger.error("Unable to fetch & populate full movie info for '" + movie.title + "' " + e)
                      }
                      fetchedMovieInfoFuture onSuccess {
                        case result: Boolean if !result => Logger.error("Unknown issue trying to fetch and populate movie info")
                      }

                    } onFailure {
                      case e: Exception => Logger.error("Issue with async execution while trying to fetch and populate full movie infor for '" + movie.title + "' " + e)
                    }
                }
                movies
              }),
              invalid = (e => {
                Logger.debug(response.status + " (GET) " + response.body)
                Logger.error("Invalid JSON during query '+s+'" + e.toString)
                Nil
              }))
          } else {
            Logger.error("GET " + response.status + " Problem trying to call tmdb " + response.body)
            Nil
          }
        }
    }
  }

  private def toMovies(s: String, tmdbMovies: List[TmdbMovie]) = {
    tmdbMovies.filter(
      movie => movie.releaseDate.isDefined && movie.posterPath.isDefined).map({
        Movie.createOrUpdate(_)
      })
  }

  // Movie
  private def fetchAndPopulateFullMovieInfo(movie: Movie): Future[Boolean] = {
    val cacheKey = "movie." + movie.tmdbId
    val cachedMovie = Cache.getAs[TmdbMovie](cacheKey)
    if (cachedMovie.isDefined) {
      Logger.debug("CACHE HIT '" + cacheKey + "'")
      cachedMovie.filter(
        movie => movie.releaseDate.isDefined && movie.posterPath.isDefined).map({
          Movie.createOrUpdate(_)
        })
      future {
        true
      }
    } else {
      WS.url("http://api.themoviedb.org/3/movie/" + movie.tmdbId)
        .withQueryString(("append_to_response", "casts"), ("api_key", Global.tmdbApiKey))
        .get().map { response =>
          if (response.status == Status.OK) {
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
              (__ \ "casts" \ "cast").readNullable[List[TmdbCast]])(TmdbMovie)

            response.json.validate[TmdbMovie].fold(
              valid = (tmdbMovie => {
                val tmdbMovieCacheTimeInSeconds = 60 * 60 * 3 //3-hour
                Cache.set(cacheKey, tmdbMovie, tmdbMovieCacheTimeInSeconds)
                if (tmdbMovie.releaseDate.isDefined && tmdbMovie.posterPath.isDefined && tmdbMovie.cast.isDefined) {
                  Movie.update(movie.id, tmdbMovie)
                  true
                } else {
                  Logger.debug("SKIP " + tmdbMovie.title + " missing fields")
                  false
                }
              }),
              invalid = (e => {
                Logger.debug(response.status + " (GET) " + response.body)
                Logger.error("Invalid JSON during query '+s+'" + e.toString)
                false
              }))
          } else {
            Logger.error("GET " + response.status + " Problem trying to call tmdb " + response.body)
            false
          }
        }
    }
  }
}