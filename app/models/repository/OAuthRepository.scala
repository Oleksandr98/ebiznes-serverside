package models.repository

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import javax.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class OAuthRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext,
                                                                               implicit val classTag: ClassTag[OAuth2Info]) extends DelegableAuthInfoDAO[OAuth2Info] {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  case class OAuth2InfoDto(id: Long, providerId: String, providerKey: String, accessToken: String, tokenType: Option[String], expiresIn: Option[Int])

  class OAuth2InfoTable(tag: Tag) extends Table[OAuth2InfoDto](tag, "OAUTH2_DATA") {
    def id = column[Long]("OAU_ID", O.PrimaryKey, O.AutoInc)

    def providerId = column[String]("OAU_PROVIDER_ID")

    def providerKey = column[String]("OAU_PROVIDER_KEY")

    def accessToken = column[String]("OAU_TOKEN")

    def tokenType = column[Option[String]]("OAU_TOKEN_TYPE")

    def expiresIn = column[Option[Int]]("OAU_EXPIRATION")

    def * = (id, providerId, providerKey, accessToken, tokenType, expiresIn) <> ((OAuth2InfoDto.apply _).tupled, OAuth2InfoDto.unapply)
  }

  val oAuth2Info = TableQuery[OAuth2InfoTable]

  override def find(loginInfo: LoginInfo): Future[Option[OAuth2Info]] = db.run {
    oAuth2Info.filter(_.providerId === loginInfo.providerID)
      .filter(_.providerKey === loginInfo.providerKey)
      .result
      .headOption
  }.map(_.map(dto => OAuth2Info(dto.accessToken, dto.tokenType, dto.expiresIn)))

  override def add(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = db.run {
    (oAuth2Info.map(c => (c.providerId, c.providerKey, c.accessToken, c.tokenType, c.expiresIn))
      returning oAuth2Info.map(_.id)
      into { case ((providerId, providerKey, accessToken, tokenType, expiresIn), id) => OAuth2InfoDto(id, providerId, providerKey, accessToken, tokenType, expiresIn) }
      ) += (loginInfo.providerID, loginInfo.providerKey, authInfo.accessToken, authInfo.tokenType, authInfo.expiresIn)
  }.map(_ => authInfo)

  override def update(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = db.run {
    oAuth2Info.filter(_.providerId === loginInfo.providerID)
      .filter(_.providerKey === loginInfo.providerKey)
      .map(u => (u.accessToken, u.tokenType, u.expiresIn))
      .update((authInfo.accessToken, authInfo.tokenType, authInfo.expiresIn))
  }.map(_ => authInfo)

  override def save(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] =
    find(loginInfo)
      .flatMap {
        case Some(_) => update(loginInfo, authInfo)
        case None => add(loginInfo, authInfo)
      }

  override def remove(loginInfo: LoginInfo): Future[Unit] = db.run {
    oAuth2Info.filter(_.providerId === loginInfo.providerID)
      .filter(_.providerKey === loginInfo.providerKey)
      .delete
  }.map(_ => Unit)
}