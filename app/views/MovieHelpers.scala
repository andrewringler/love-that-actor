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
import tmdb.TmdbService

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
    TmdbService.tmdbConfigSlow.baseUrl + "w92" + movie.posterPath
  }
  def w154(movie: Movie) :String = {
    TmdbService.tmdbConfigSlow.baseUrl + "w154" + movie.posterPath
  }
  def w185(movie: Movie) :String = {
    TmdbService.tmdbConfigSlow.baseUrl + "w185" + movie.posterPath
  }
  def w342(movie: Movie) :String = {
    TmdbService.tmdbConfigSlow.baseUrl + "w342" + movie.posterPath
  }
  def w500(movie: Movie) :String = {
    TmdbService.tmdbConfigSlow.baseUrl + "w500" + movie.posterPath
  }
  def wOriginal(movie: Movie) :String = {
    TmdbService.tmdbConfigSlow.baseUrl + "original" + movie.posterPath
  }
}