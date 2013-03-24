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
  val searchForm = Form(
    "s" -> nonEmptyText)

  def index = Action {
    Ok(views.html.index(Like.all(), searchForm))
  }

  // Likes
  def likes = Action {
    Ok(views.html.likes(Like.all()))
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
    Ok(views.html.search(Nil, searchForm))
  }

  def search() = Action { implicit request =>
    searchForm.bindFromRequest.fold(
      errors => BadRequest(views.html.search(Nil, errors)),
      s => {
        val searchTerm = s.toLowerCase()
        val cacheKey = "search."+searchTerm
        val cachedSearch = Cache.getAs[List[TmdbMovie]](cacheKey)
        if (cachedSearch.isDefined) {
          Logger.debug("CACHE HIT '"+cacheKey+"'")
          Ok(views.html.search(cachedSearch.get.map(Movie.createOrUpdate(_)), searchForm))
        } else {
          Async {
            WS.url("http://api.themoviedb.org/3/search/movie")
              .withQueryString(("query", searchTerm), ("api_key", globals.tmdbApiKey))
              .get().map { response =>

                implicit val movieReads = (
                  (__ \ "title").read[String] ~
                  (__ \ "release_date").read[Date] ~
                  (__ \ "id").read[Long])(TmdbMovie)
                implicit val searchReads = (
                  (__ \ "total_results").read[Int] ~
                  (__ \ "results").read[List[TmdbMovie]])(Search)

                Logger.debug(response.status + " (GET) " + response.body)

                response.json.validate[Search].fold(
                  valid = (search => 
                    Ok(views.html.search(tmdbMoviesSetOnCacheAndGet(searchTerm, search.movies).map {
                        Movie.createOrUpdate(_)
                    }, searchForm))),
                  invalid = (e => BadRequest(e.toString)))
              }
          }
        }
      })
  }
  
  def tmdbMoviesSetOnCacheAndGet(s: String, tmdbMovies: List[TmdbMovie]) = {
	  Cache.set("search."+s, tmdbMovies, 60*60)
	  tmdbMovies
  }
}