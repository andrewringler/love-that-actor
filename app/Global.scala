import play.api._

package object globals extends GlobalSettings {
  var tmdbApiKeyOption = Play.current.configuration.getString("tmdb.apikey")
  if(tmdbApiKeyOption.isEmpty || tmdbApiKeyOption.get == ""){
    throw new IllegalStateException("You must set an environment variable with TMDB_KEY equal to your TheMovieDb API Key")
  }
  val tmdbApiKey = tmdbApiKeyOption.get
}