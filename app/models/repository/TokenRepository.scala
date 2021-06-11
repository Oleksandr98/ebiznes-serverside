package models.repository

import javax.inject.{Inject, Singleton}
import models.Token
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@Singleton
class TokenRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class AuthTokenTable(tag: Tag) extends Table[Token](tag, "TOKENS") {
    def id = column[Long]("TKN_ID", O.PrimaryKey, O.AutoInc)

    def userId = column[Long]("TKN_CTM_ID")

    def * = (id, userId) <> ((Token.apply _).tupled, Token.unapply)
  }

  val authToken = TableQuery[AuthTokenTable]

  def create(userId: Long): Future[Token] = db.run {
    (authToken.map(r => r.userId)
      returning authToken.map(_.id)
      into { case (userId, id) => Token(id, userId) }
      ) += userId
  }
}