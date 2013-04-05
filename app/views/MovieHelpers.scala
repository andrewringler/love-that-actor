package views

import play.api.cache.Cache
import play.api.libs.ws.WS
import play.api.libs.json._
import play.api.data.validation.ValidationError
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.Logger
import models.Movie
import play.api.Play.current

object MovieHelpers {
  def moviesFoundMessage(numberOfMoviesFound: Int) :String = {
    if(numberOfMoviesFound == 0) {
      "Woah, sorry, no movies found :("
    } else if (numberOfMoviesFound == 1){
      "We found 1 movie"
    } else {
    	numberOfMoviesFound + " movies found"      
    }
  }
  
  //	"poster_sizes": ["w92", "w154", "w185", "w342", "w500", "original"],
  def w92(movie: Movie) :String = {
    tmdbConfigSlow.baseUrl + "w92" + movie.posterPath
  }
  def w154(movie: Movie) :String = {
    tmdbConfigSlow.baseUrl + "w154" + movie.posterPath
  }
  def w185(movie: Movie) :String = {
    tmdbConfigSlow.baseUrl + "w185" + movie.posterPath
  }
  def w342(movie: Movie) :String = {
    tmdbConfigSlow.baseUrl + "w342" + movie.posterPath
  }
  def w500(movie: Movie) :String = {
    tmdbConfigSlow.baseUrl + "w500" + movie.posterPath
  }
  def wOriginal(movie: Movie) :String = {
    tmdbConfigSlow.baseUrl + "original" + movie.posterPath
  }

  // TODO pull poster size from config
  // TODO TMDB service calls should be in a model or app service tier
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
//                  Logger.debug(response.status + " (GET) " + response.body)
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
}