package modules

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.oauth2.{DropboxProvider, DropboxProfileParser}
import com.mohiva.play.silhouette.impl.providers.oauth2.DropboxProvider.{ID, SpecifiedProfileError}
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, CommonSocialProfileBuilder, OAuth2Info, OAuth2Settings, SocialStateHandler}
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import play.api.libs.json.JsValue

import scala.concurrent.Future

class CustomDropboxProvider(
                             override protected val httpLayer: HTTPLayer,
                             override protected val stateHandler: SocialStateHandler,
                             override val settings: OAuth2Settings)
  extends DropboxProvider(httpLayer, stateHandler, settings) with CommonSocialProfileBuilder {

  override val profileParser = new CustomDropboxProfileParser

  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api")).withHttpHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}",
      CONTENT_TYPE -> "").post("").flatMap { response =>
      val json = response.json
      response.status match {
        case 200 => profileParser.parse(json, authInfo)
        case status =>
          val error = (json \ "error").as[String]
          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, error, status))
      }
    }
  }

}

class CustomDropboxProfileParser extends DropboxProfileParser {

  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "account_id").as[String]
    val firstName = (json \ "name" \ "given_name").asOpt[String]
    val lastName = (json \ "name" \ "surname").asOpt[String]
    val fullName = (json \ "name" \ "display_name").asOpt[String]
    val email = (json \ "email").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      email = email)
  }
}
