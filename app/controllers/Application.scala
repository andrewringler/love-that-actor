package controllers

import models.Like
import models.Movie
import play.api.Logger
import play.api.Play.current
import play.api.Routes
import play.api.cache.Cache
import play.api.data.Form
import play.api.data.Forms.nonEmptyText
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller
import tmdb.TmdbService

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

  // Movies
  def movie(id: Long) = Action {
    Ok(views.html.movie(Movie.getMovie(id), searchForm))
  }

  // Search
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
        TmdbService.tmdbMovieSearch(searchTerm).map({
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
            TmdbService.tmdbMovieSearch(searchTerm).map({
              case Some(movies) => Ok(views.html.searchResults(movies, searchForm))
              case None => BadRequest("Issue with TMDB") // TODO handle more robustly
            })
          }
        }
      })
  }
}