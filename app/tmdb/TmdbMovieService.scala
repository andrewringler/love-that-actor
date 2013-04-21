package tmdb

import models.Movie
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
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
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.http.Status
import nl.grons.sentries.SentrySupport
import scala.util.control.Exception._
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import nl.grons.sentries.SentrySupport
import nl.grons.sentries.support._
import nl.grons.sentries.core.LoadBalancer
import nl.grons.sentries.cross.Concurrent.Duration
import scala.concurrent._
import scala.concurrent.duration._
import com.google.common.cache.LoadingCache

object TmdbMovieService {
  /* Keep track of currently pending movie requests so that we don't make
   * the same movie request twice before it has had a chance to complete
   * and be stored in the database
   */
  private val pendingRequests =
    CacheBuilder.newBuilder()
      .expireAfterAccess(5, java.util.concurrent.TimeUnit.SECONDS)
      .build(new CacheLoader[Movie, scala.concurrent.Future[Boolean]]() {
        override def load(movie: Movie): scala.concurrent.Future[Boolean] = {
          tmdbMovieWebServiceCall(movie)
        }
      })

  /*
   * Query for full movie info from Tmdb asynchronously
   * and populate in our database
   * retry forever when reaching Tmdb API limits
   */
  def populateTmdbFullMovieInfoAsync(movie: Movie) {
    Akka.future {
      populateTmdbFullMovieInfo(movie)
    } onFailure {
      case e: Exception => Logger.error("!1 issue trying to populate full movie info for " + movie.abbrTitle, e)
    }
  }

  /*
   * Query for full movie info from Tmdb
   * and populate in our database
   * retry forever when reaching Tmdb API limits
   */
  def populateTmdbFullMovieInfo(movie: Movie) {
    if (movie.needsUpdate) {
      var retry = false
      do {
        try {
          retry = false
          val result = pendingRequests.get(movie)
          result onFailure {
            case e: Exception => Logger.error("!2 issue trying to populate full movie info for " + movie.abbrTitle, e)
          }
          result onSuccess {
            case result: Boolean if !result => Logger.error("!3 issue trying to populate full movie info for " + movie.abbrTitle + " check previous logs")
          }
        } catch {
          case e: Exception if e.getCause().isInstanceOf[NotAvailableException] => {
            //            Logger.debug("!4 exceeded api limits retrying fetching " + movie.abbrTitle)
            retry = true
          }
        }
      } while (retry)
    }
  }

  private def tmdbMovieWebServiceCall(movie: Movie): Future[Boolean] = {
    TmdbService.tmdbApiSentry {
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
                if (tmdbMovie.releaseDate.isDefined && tmdbMovie.posterPath.isDefined && tmdbMovie.cast.isDefined) {
                  Movie.update(movie.id, tmdbMovie)
                  Logger.info("added '" + movie.title)
                  true
                } else {
                  Logger.debug("SKIP " + tmdbMovie.title + " missing fields")
                  true
                }
              }),
              invalid = (e => {
                Logger.error("!5 invalid json returned " + e)
                Logger.debug("!6 response body " + response.body)
                false
              }))
          } else if (response.status == Status.SERVICE_UNAVAILABLE) {
            // TMDB API Limit exceeded
            Logger.warn("! oops we got a " + Status.SERVICE_UNAVAILABLE + " from tmdb")
            throw new NotAvailableException("movie", "oops we got a " + Status.SERVICE_UNAVAILABLE + " from tmdb", new Throwable)
          } else {
            // TODO handle 503 (unavailable)
            Logger.error("!7 invalid response status " + response.status + " body= " + response.body)
            false
          }
        }
    }
  }
}