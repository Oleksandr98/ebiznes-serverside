
package controllers

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.repository.CustomerRepository
import models.{User, WSCustomerData}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Cookie.SameSite
import play.api.mvc._
import play.filters.csrf.CSRF.Token
import play.filters.csrf.{CSRF, CSRFAddToken}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(scc: DefaultSilhouetteControllerComponents, addToken: CSRFAddToken,
                               customerRepo: CustomerRepository)(implicit ex: ExecutionContext) extends SilhouetteController(scc) {

  protected def authenticateUser(user: User)(implicit request: RequestHeader): Future[AuthenticatorResult] = {
    authenticatorService.create(user.loginInfo)
      .flatMap { authenticator =>
        authenticatorService.init(authenticator).flatMap { v =>
          authenticatorService.embed(v, Ok("Authenticated"))
        }
      }
  }

  def signIn: Action[AnyContent] = addToken(unsecuredAction.async { implicit request: Request[AnyContent] =>
    val json = request.body.asJson.get
    val Token(name, value) = CSRF.getToken.get
    val signInRequest = json.as[SignInRequest]
    val credentials = Credentials(signInRequest.email, signInRequest.password)

    credentialsProvider.authenticate(credentials).flatMap { loginInfo =>
      userRepository.retrieve(loginInfo).flatMap {
        case Some(user) =>
          authenticateUser(user)
            .map(_.withCookies(Cookie(name, value, httpOnly = false)).withHeaders(("Access-Control-Allow-Credentials", "true"),
              ("Access-Control-Allow-Origin", "https://ebiznes-clientside.azurewebsites.net"),
              ("Access-Control-Expose-Headers", "csrftoken"), ("csrftoken", value)))
        case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
      }
    }.recover {
      case _: ProviderException =>
        Forbidden("Wrong credentials")
          .withCookies(Cookie("PLAY_SESSION", "", Some(Cookie.DiscardedMaxAge), "/", Option.apply(""), true, false, Option.apply(SameSite.None)))
    }
  })

  def signOut: Action[AnyContent] = securedAction.async { implicit request: SecuredRequest[EnvType, AnyContent] =>
    authenticatorService.discard(request.authenticator, Ok("Logged out"))
      .map(_.withCookies(
        Cookie("csrfToken", "", Some(Cookie.DiscardedMaxAge), "/", Option.apply(""), true, false, Option.apply(SameSite.None)),
        Cookie("PLAY_SESSION", "", Some(Cookie.DiscardedMaxAge), "/", Option.apply(""), true, false, Option.apply(SameSite.None)),
        Cookie("OAuth2State", "", Some(Cookie.DiscardedMaxAge), "/", Option.apply(""), true, false, Option.apply(SameSite.None)),
        Cookie("authenticator", "", Some(Cookie.DiscardedMaxAge), "/", Option.apply(""), true, false, Option.apply(SameSite.None))
      ))
  }

  def signUp: Action[AnyContent] = unsecuredAction.async { implicit request: Request[AnyContent] =>
    val json = request.body.asJson.get
    val signUpRequest = json.as[WSCustomerData]
    val loginInfo = LoginInfo(CredentialsProvider.ID, signUpRequest.login)

    userRepository.retrieve(loginInfo).flatMap {
      case Some(_) =>
        Future.successful(Forbidden("User already exists"))
      case None =>
        val authInfo = passwordHasherRegistry.current.hash(signUpRequest.password)
        userRepository.create(
          CredentialsProvider.ID,
          signUpRequest.login,
          signUpRequest.login
        ).flatMap { user =>
          customerRepo.create(CredentialsProvider.ID, signUpRequest.name, signUpRequest.surname, signUpRequest.login, signUpRequest.birthDate)
            .map(_ => user)
        }.flatMap { user =>
            authInfoRepository.add(loginInfo, authInfo)
              .map(_ => user)
          }.flatMap { user =>
          authTokenRepository.create(user.id)
            .map(_ => user)
        }.map { user =>
          Json.toJson(user)
        }.map { json =>
          Created(json)
        }
    }
  }

  case class SignInRequest(email: String, password: String)

  object SignInRequest {
    implicit val signInRequestForm: OFormat[SignInRequest] = Json.format[SignInRequest]
  }

  case class SignUpRequest(email: String, password: String)

  object SignUpRequest {
    implicit val signUpRequestForm: OFormat[SignUpRequest] = Json.format[SignUpRequest]
  }

}