package models.repository

import models.{CustomerData, CustomerStatuses, WSCustomerData, WSUpdateCustomerData}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class CustomerRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class CustomerTable(tag: Tag) extends Table[CustomerData](tag, "CUSTOMERS") {

    def id = column[Long]("CTM_ID", O.PrimaryKey, O.AutoInc)

    def name = column[String]("CTM_NAME")

    def surname = column[String]("CTM_SURNAME")

    def status = column[String]("CTM_STATUS")

    def closureDate = column[Option[Date]]("CTM_CLOSURE_DATE")

    def modifyDate = column[Option[Date]]("CTM_MODIFY_DATE")

    def createDate = column[Option[Date]]("CTM_CREATE_DATE")

    def providerId = column[String]("CTM_PROVIDER_ID")

    def login = column[String]("CTM_LOGIN")

    def birthDate = column[Date]("CTM_BIRTH_DATE")

    def * = (id, name, surname, status, createDate, providerId, modifyDate, closureDate, login, birthDate) <> ((CustomerData.apply _).tupled, CustomerData.unapply)
  }

  private val customerData = TableQuery[CustomerTable]

  def create(providerId: String, name: String, surname: String, login: String, birthDate: Date): Future[CustomerData] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (customerData.map(c => (c.name, c.surname, c.status, c.createDate, c.providerId, c.modifyDate, c.closureDate, c.login, c.birthDate))
      returning customerData.map(_.id)
      into {case ((name, surname, status, createDate, providerKey, modifyDate, closureDate, login, birthDate), id) =>
                  CustomerData(id, name, surname, status, createDate, providerKey, modifyDate, closureDate, login, birthDate)}
      ) += (name, surname, CustomerStatuses.active.toString, currentDate, providerId, Option.empty, Option.empty,
      login, birthDate)
  }

  def getAll(): Future[Seq[CustomerData]] = db.run {
    customerData.filter(c => c.closureDate.column.isEmpty).result
  }

  def getById(id: Long): Future[Option[CustomerData]] = db.run {
    customerData.filter(c => c.id === id && c.closureDate.column.isEmpty).result.headOption
  }

  def closeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    customerData.filter(c => c.id === id && c.closureDate.column.isEmpty).map(v => (v.closureDate, v.status)).update(currentDate, CustomerStatuses.closed.toString)
  }

  def blockOrUnblockById(id: Long, block: Boolean): Future[Int] = db.run {
    val status = if (block) CustomerStatuses.blocked.toString else CustomerStatuses.active.toString
    customerData.filter(c => c.id === id && c.closureDate.column.isEmpty).map(v => v.status).update(status)
  }

  def updateById(id: Long, cData: WSUpdateCustomerData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "CTM_MODIFY_DATE = " + currentDate.get.getTime
    if (cData.name.isDefined) updateQuery += ", CTM_NAME = '" + cData.name.get + "'"
    if (cData.surname.isDefined) updateQuery += ", CTM_SURNAME = '" + cData.surname.get + "'"
    if (cData.login.isDefined) updateQuery += ", CTM_LOGIN = '" + cData.login.get + "'"
    if (cData.birthDate.isDefined) updateQuery += ", CTM_BIRTH_DATE = " + cData.birthDate.get.getTime
    db.run(sql"UPDATE CUSTOMERS SET #$updateQuery WHERE CTM_ID = $id AND CTM_CLOSURE_DATE IS NULL".asUpdate)
  }

}

