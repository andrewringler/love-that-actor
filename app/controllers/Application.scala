package controllers

import models.Like
import models.Movie
import play.api.Logger
import play.api.Play.current
import play.api.Routes
import play.api.data.Form
import play.api.data.Forms.nonEmptyText
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller
import tmdb.TmdbService
import tmdb.TmdbSearchService

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

  def searchAutoComplete(s: String) = Action {
    Async {
      TmdbSearchService.tmdbMovieAutoComplete(s).map {
        case movies => Ok(moviesToJSonAutocomplete(movies))
      }
    }
  }

  def search() = Action { implicit request =>
    searchForm.bindFromRequest.fold(
      errors => BadRequest(views.html.searchResults(Nil, errors)),
      s => {
        Async {
          TmdbSearchService.tmdbMovieSearch(s).map {
            case movies => Ok(views.html.searchResults(movies, searchForm))
          }
        }
      })
  }
}