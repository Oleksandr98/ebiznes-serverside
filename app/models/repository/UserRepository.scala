package models.repository

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.util.PasswordInfo

import javax.inject.{Inject, Singleton}
import models.User
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext, implicit val classTag: ClassTag[PasswordInfo]) extends IdentityService[User] {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  case class UserDto(id: Long, providerId: String, providerKey: String, email: String, loginDate: Date)

  class UserTable(tag: Tag) extends Table[UserDto](tag, "USERS") {
    def id = column[Long]("USR_ID", O.PrimaryKey, O.AutoInc)

    def providerId = column[String]("USR_PROVIDER_ID")

    def providerKey = column[String]("USR_PROVIDER_KEY")

    def email = column[String]("USR_EMAIL")

    def loginDate = column[Date]("USR_LOGIN_DATE")

    def * = (id, providerId, providerKey, email, loginDate) <> ((UserDto.apply _).tupled, UserDto.unapply)
  }

  val user = TableQuery[UserTable]

  override def retrieve(loginInfo: LoginInfo): Future[Option[User]] = db.run {
    user.filter(_.providerId === loginInfo.providerID)
      .filter(_.providerKey === loginInfo.providerKey)
      .result
      .headOption
  }.map(_.map(dto => toModel(dto)))

  def create(providerId: String, providerKey: String, email: String): Future[User] = db.run {
    val currentDate = new Date(new java.util.Date().getTime)
    (user.map(c => (c.providerId, c.providerKey, c.email, c.loginDate))
      returning user.map(_.id)
      into { case ((providerId, providerKey, email, loginDate), id) => UserDto(id, providerId, providerKey, email, loginDate) }
      ) += (providerId, providerKey, email, currentDate)
  }.map(dto => toModel(dto))

  def getAll: Future[Seq[User]] = db.run {
    user.result
  }.map(_.map(dto => toModel(dto)))

  def getByIdOption(id: Long): Future[Option[User]] = db.run {
    user.filter(_.id === id).result.headOption
  }.map(_.map(dto => toModel(dto)))

  def getById(id: Long): Future[User] = db.run {
    user.filter(_.id === id).result.head
  }.map(dto => toModel(dto))

  def update(id: Long, newUser: User): Future[User] = {
    val userToUpdate = newUser.copy(id)
    db.run {
      user.filter(_.id === id)
        .update(toDto(userToUpdate))
        .map(_ => userToUpdate)
    }
  }

  def delete(id: Long): Future[Unit] =
    db.run {
      user.filter(_.id === id)
        .delete
        .map(_ => ())
    }

  private def toModel(dto: UserDto): User =
    User(dto.id, LoginInfo(dto.providerId, dto.providerKey), dto.email, dto.loginDate)

  private def toDto(model: User): UserDto =
    UserDto(model.id, model.loginInfo.providerID, model.loginInfo.providerKey, model.email, model.loginDate)
}