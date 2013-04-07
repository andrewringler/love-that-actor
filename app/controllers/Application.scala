package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models.Like
import models.Movie
import play.api.Logger
import models.TmdbMovie
import java.util.Date
import play.api.Play.current
import play.api.cache.Cache
import models.TmdbMovie
import models.TmdbMovie

object Application extends Controller {
  def javascriptRoutes = Action { implicit request =>
    Ok(Routes.javascriptRouter("jsRoutes")(controllers.routes.javascript.Application.searchAutoComplete))
      .as("text/javascript")
  }

  val searchForm = Form(
    "q" -> nonEmptyText)

  def index = Action {
    Ok(views.html.index(Like.all(), searchForm))
  }

  // Likes
  def likes = Action {
    Ok(views.html.likes(Like.all(), searchForm))
  }

  def newLike(movieId: Long) = Action {
    Like.create(movieId)
    Redirect(routes.Application.index)
  }

  def deleteLike(id: Long) = Action {
    Like.delete(id)
    Redirect(routes.Application.index)
  }

  // Search
  import play.api.libs.json._
  import play.api.data.validation.ValidationError
  import play.api.libs.ws.WS
  import play.api.libs.concurrent.Execution.Implicits._
  import play.api.libs.functional.syntax._

  case class Search(
    total: Int = 0,
    movies: List[TmdbMovie] = Nil)

  def newSearch() = Action {
    Ok(views.html.search(searchForm))
  }

  def moviesToJSonAutocomplete(movies: List[Movie]): JsValue = {
    val titles = movies.take(5).map { case movie => Json.toJson(movie.title) }
    Json.toJson(titles)
  }

  def searchAutoComplete(query: String) = Action {
    val searchTerm = query.toLowerCase()
    val cacheKey = "search." + searchTerm
    val cachedSearch = Cache.getAs[List[Movie]](cacheKey)
    if (cachedSearch.isDefined) {
      Logger.debug("CACHE HIT '" + cacheKey + "'")
      Ok(moviesToJSonAutocomplete(cachedSearch.get))
    } else {
      Async {
        tmdbMovieSearch(searchTerm).map({
          case Some(movies) => Ok(moviesToJSonAutocomplete(movies))
          case None => Ok(Json.arr())
        })
      }
    }
  }

  def search() = Action { implicit request =>
    searchForm.bindFromRequest.fold(
      errors => BadRequest(views.html.searchResults(Nil, errors)),
      s => {
        val searchTerm = s.toLowerCase()
        val cacheKey = "search." + searchTerm
        val cachedSearch = Cache.getAs[List[Movie]](cacheKey)
        if (cachedSearch.isDefined) {
          Logger.debug("CACHE HIT '" + cacheKey + "'")
          Ok(views.html.searchResults(cachedSearch.get, searchForm))
        } else {
          Async {
            tmdbMovieSearch(searchTerm).map({
              case Some(movies) => Ok(views.html.searchResults(movies, searchForm))
              case None => BadRequest("Issue with TMDB") // TODO handle more robustly
            })
          }
        }
      })
  }

  def tmdbMovieSearch(s: String): scala.concurrent.Future[Option[List[Movie]]] = {
    WS.url("http://api.themoviedb.org/3/search/movie")
      .withQueryString(("query", s), ("api_key", Global.tmdbApiKey))
      .get().map { response =>
        implicit val movieReads = (
          (__ \ "title").read[String] ~
          (__ \ "release_date").readNullable[Date] ~
          (__ \ "id").read[Long] ~
          (__ \ "poster_path").readNullable[String])(TmdbMovie)
        implicit val searchReads = (
          (__ \ "total_results").read[Int] ~
          (__ \ "results").read[List[TmdbMovie]])(Search)

        //        Logger.debug(response.status + " (GET) " + response.body)

        response.json.validate[Search].fold(
          valid = (search =>
            Option.apply(tmdbMoviesSetOnCacheAndGet(s, search.movies))),
          invalid = (e => {
            Logger.error("Invalid JSON during query '+s+'" + e.toString)
            Option.empty
          }))
      }
  }

  def tmdbMoviesSetOnCacheAndGet(s: String, tmdbMovies: List[TmdbMovie]) = {
    val movies = tmdbMovies.filter(
        movie => movie.releaseDate.isDefined && movie.posterPath.isDefined
     ).map({
      Movie.createOrUpdate(_)
    })
    Cache.set("search." + s, movies, 60 * 60)
    movies
  }
}