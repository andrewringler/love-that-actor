import play.api._

package object Global extends GlobalSettings {
  // play has already throw an exception if this key is missing
  val tmdbApiKey = Play.current.configuration.getString("tmdb.apikey").get 
}