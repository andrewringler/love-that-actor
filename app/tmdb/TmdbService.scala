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

object TmdbService extends SentrySupport {
  /* Rate Limiting 
	 * http://docs.themoviedb.apiary.io
	 * 30 requests every 10 seconds per IP
	 * Maximum 20 simultaneous connections
	 */
  val tmdbApiSentry = sentry("tmdb").withMetrics
    //    .withFailLimit(failLimit = 1, retryDelay = 250 millis)
    .withConcurrencyLimit(20)
    .withRateLimit(30, (10 seconds).toMillis)

  // Config
  // TODO pull poster size from config
  // TODO should not be holding up app startup for this
  val tmdbConfigSlow: TmdbConfig = tmdbGetConfigSlow

  case class TmdbConfig(baseUrl: String, secureBaseUrl: String)

  def tmdbGetConfigSlow(): TmdbConfig = {
    Cache.getOrElse[TmdbConfig]("tmdb.config") {
      tmdbApiSentry {
        val result = WS.url("http://api.themoviedb.org/3/configuration")
          .withQueryString(("api_key", Global.tmdbApiKey))
          .get().map { response =>
            if (response.status == Status.OK) {
              implicit val tmdbConfigReads: Reads[TmdbConfig] = (
                (__ \ "images" \ "base_url").read[String] ~
                (__ \ "images" \ "secure_base_url").read[String])(TmdbConfig)
              response.json.validate[TmdbConfig].fold(
                valid = (config =>
                  config),
                invalid = (e => {
                  Logger.error("Invalid JSON " + e.toString)
                  // TODO handle
                  throw new RuntimeException
                }))
            } else {
              Logger.error("GET " + response.status + " Problem trying to call tmdb " + response.body)
              // TODO handle
              throw new RuntimeException
            }
          }
        Await.result(result, 15 seconds)
      }
    }
  }
}