package tmdb

import java.util.Date
import scala.concurrent.ExecutionContext.Implicits.global
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
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.http.Status
import scala.concurrent._
import nl.grons.sentries.support.NotAvailableException
import nl.grons.sentries.SentrySupport

case class SearchResult(
  total: Int = 0,
  movies: List[TmdbMovie] = Nil)

case class SearchRequest(query: String)(val blockOnCongestion: Boolean)

object TmdbSearchService extends SentrySupport {
  /* Keep track of currently pending searches so that we don't make
   * the same search request twice before it has had a chance to complete
   * and be cached locally
   */
  private val pendingSearches =
    CacheBuilder.newBuilder()
      .expireAfterAccess(5, java.util.concurrent.TimeUnit.SECONDS)
      .build(new CacheLoader[SearchRequest, scala.concurrent.Future[List[Movie]]]() {
        override def load(searchRequest: SearchRequest): scala.concurrent.Future[List[Movie]] = {
          tmdbMovieSearchWebServiceCall(searchRequest.query)
        }
      })
  /* Cache results of search requests with Tmdb since
   * we are not going to be saving them in the database
   */
  class SearchResultsCache {
    val tmdbSearchCacheTimeInSeconds = 60 * 60 //1-hour
    private def cacheKey(s: String): String = {
      "search." + s
    }

    def get(query: String): Option[SearchResult] = {
      Cache.getAs[SearchResult](cacheKey(query))
    }

    def set(query: String, results: SearchResult) {
      Cache.set(cacheKey(query), results, tmdbSearchCacheTimeInSeconds)
    }
  }
  val searchResultsCache = new SearchResultsCache

  /*
   * Search for movies by name from Tmdb
   * since this is an autocomplete request we will
   * just silently fail when reaching Tmdb API limits
   */
  def tmdbMovieAutoComplete(s: String): scala.concurrent.Future[List[Movie]] = {
    val searchRequest = SearchRequest(s.toLowerCase())(false)
    searchResultsCache.get(searchRequest.query).map {
      searchResult => future { toMovies(s, searchResult.movies) }
    }.getOrElse {
      try {
        pendingSearches.get(searchRequest)
      } catch {
        case e: Exception => {
          // TODO should only be failing silently for Tmdb API limit errors, not ALL errors
          Logger.debug("Issue trying to submit auto-complete request: ", e)
          future { Nil }
        }
      }
    }
  }

  /*
   * Search for movies by name from Tmdb
   * retry forever when reaching Tmdb API limits
   */
  def tmdbMovieSearch(s: String): scala.concurrent.Future[List[Movie]] = {
    val searchRequest = SearchRequest(s.toLowerCase())(true)
    searchResultsCache.get(searchRequest.query).map {
      searchResult => future { toMovies(s, searchResult.movies) }
    }.getOrElse {
      var retry = false
      var result = future { List[Movie]() }
      do {
        retry = false
        try {
          result = pendingSearches.get(searchRequest)
        } catch {
          case e: ExecutionException if e.getCause().isInstanceOf[NotAvailableException] => {
            Logger.debug("API Limit Reached, retrying for '+s+'")
            retry = true
          }
        }
      } while (retry)
      result
    }
  }

  private def tmdbMovieSearchWebServiceCall(s: String): scala.concurrent.Future[List[Movie]] = TmdbService.tmdbApiSentry {
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
            (__ \ "results").read[List[TmdbMovie]])(SearchResult)

          response.json.validate[SearchResult].fold(
            valid = (search => {
              searchResultsCache.set(s, search)
              val movies = toMovies(s, search.movies)
              movies foreach {
                case movie =>
                  TmdbMovieService.populateTmdbFullMovieInfoAsync(movie)
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

  private def toMovies(s: String, tmdbMovies: List[TmdbMovie]) = {
    tmdbMovies.filter(
      movie => movie.releaseDate.isDefined && movie.posterPath.isDefined).map({
        Movie.createOrUpdate(_)
      })
  }
}