package models
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Play.current

case class Like(id: Long, label: String)

object Like {
  val like = {
    get[Long]("id") ~
      get[String]("label") map {
        case id ~ label => Like(id, label)
      }
  }

  def all(): List[Like] = DB.withConnection { implicit c =>
    SQL("select * from likes order by label").as(like *)
  }

  def create(label: String) {
    DB.withConnection { implicit c =>
      SQL("insert into likes (label) values ({label})").on(
        'label -> label).executeUpdate()
    }
  }

  def delete(id: Long) {
    DB.withConnection { implicit c =>
      SQL("delete from likes where id = {id}").on(
        'id -> id).executeUpdate()
    }
  }
}