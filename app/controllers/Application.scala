package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models.Like
import models.Movie
import play.api.Logger

object Application extends Controller {
  val searchForm = Form(
    "s" -> nonEmptyText)

  def index = Action {
    Ok(views.html.index(Like.all(), Nil, searchForm))
  }

  // Likes
  def likes = Action {
    Ok(views.html.likes(Like.all()))
  }
  
  def newLike(label: String) = Action {
	Like.create(label)
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
    movies: List[Movie] = Nil
  )

  def newSearch() = Action {
  	Ok(views.html.search(Nil, searchForm))
  }
  
  def search() = Action { implicit request =>
    searchForm.bindFromRequest.fold(
      errors => BadRequest(views.html.search(Nil, errors)),
      s => {
		    Async {
		      WS.url("http://api.themoviedb.org/3/search/movie")
		        .withQueryString(("query", s), ("api_key", globals.tmdbApiKey))
		        .get().map { response =>
		
		          implicit val movieReads = (
		            (__ \ "title").read[String] ~
		            (__ \ "release_date").read[String] ~
		            (__ \ "id").read[Int]
		          )(Movie)
		          implicit val searchReads = (
		            (__ \ "total_results").read[Int] ~
		            (__ \ "results").read[List[Movie]]
		          )(Search)
		
		          Logger.debug(response.status + " (GET) " + response.body)
		          
		          response.json.validate[Search].fold(
		            valid = (search =>
		              Ok(views.html.search(search.movies, searchForm))
		            ),
		            invalid = (e => BadRequest(e.toString)))
		      }
		    }
      })
  }
}