package controllers

import java.sql.Connection
import java.util.UUID
import models.orm.SquerylPostgreSqlAdapter
import models.upload.OverviewUpload
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Session
import play.api.Play.current
import play.api.db.DB
import play.api.libs.iteratee.Error
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ Action, BodyParser, BodyParsers, Request, RequestHeader, Result }
import play.api.mvc.AnyContent
import models.upload.LO
import models.orm.DocumentSet
import models.orm.DocumentSetType._
import models.orm.User

/**
 * Handles a file upload, storing the file in a LargeObject, updating the upload table,
 * and starting a DocumentSetCreationJob. Most of the work related to the upload happens
 * in FileUploadIteratee.
 */
trait UploadController extends BaseController {

  // authorizeInTransaction and authorizedBodyParser don't belong here.
  // Should move into BaseController and/or TransactionAction, but it's not
  // clear how, since the usage here flips the dependency
  protected def authorizeInTransaction(authority: Authority)(implicit r: RequestHeader) = {
    DB.withTransaction { implicit connection =>
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)
      using(session) { // sets thread-local variable
        authorized(authority)
      }
    }
  }

  // Move this along with authorizeInTransaction
  def authorizedBodyParser[A](authority: Authority)(f: User => BodyParser[A]) = parse.using { implicit request =>
    authorizeInTransaction(authority) match {
      case Left(e) => parse.error(e)
      case Right(user) => f(user)
    }
  }

  /** @return state of upload */
  def show(guid: UUID) = authorizedAction(anyUser) { user => authorizedShow(user, guid)(_: Request[AnyContent], _: Connection) }

  /** Handle file upload and kick of documentSetCreationJob */
  def create(guid: UUID) = ActionInTransaction(authorizedFileUploadBodyParser(guid)) { authorizedCreate(guid)(_: Request[OverviewUpload], _: Connection) }

  private def uploadResult(upload: OverviewUpload) =
    if (upload.uploadedFile.size == 0) NotFound
    else if (upload.uploadedFile.size == upload.size) Ok
    else PartialContent

  private[controllers] def authorizedShow(user: User, guid: UUID)(implicit request: Request[AnyContent], connection: Connection) = {
    def contentRange(upload: OverviewUpload): String = "0-%d/%d".format(upload.uploadedFile.size - 1, upload.size)
    def contentDisposition(upload: OverviewUpload): String = upload.uploadedFile.contentDisposition

    findUpload(user.id, guid).map { u =>
      uploadResult(u) match {
        case NotFound => NotFound
        case r => r.withHeaders(
          (CONTENT_RANGE, contentRange(u)),
          (CONTENT_DISPOSITION, contentDisposition(u)))
      }
    } getOrElse (NotFound)
  }

  private[controllers] def authorizedCreate(guid: UUID)(implicit request: Request[OverviewUpload], connection: Connection) = {
    val upload: OverviewUpload = request.body
    
    val result = uploadResult(upload)
    if (result == Ok) {
      startDocumentSetCreationJob(upload)
      deleteUpload(upload)
    }
    
    result
  }
  
  /** Gets the guid and user info to the body parser handling the file upload */
  def authorizedFileUploadBodyParser(guid: UUID) = authorizedBodyParser(anyUser) { user => fileUploadBodyParser(user, guid) }

  def fileUploadBodyParser(user: User, guid: UUID): BodyParser[OverviewUpload] = BodyParser("File upload") { request =>
    fileUploadIteratee(user.id, guid, request)
  }

  protected def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]]
  protected def findUpload(userId: Long, guid: UUID): Option[OverviewUpload]
  protected def deleteUpload(upload: OverviewUpload)
  protected def startDocumentSetCreationJob(upload: OverviewUpload)
}

/**
 * UploadController implementation that uses FileUploadIteratee
 */
object UploadController extends UploadController with PgConnection {

  def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
    FileUploadIteratee.store(userId, guid, requestHeader)

  def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = OverviewUpload.find(userId, guid)

  def deleteUpload(upload: OverviewUpload) = withPgConnection { implicit c =>
    upload.delete
  }

  def startDocumentSetCreationJob(upload: OverviewUpload) {
    val documentSet = DocumentSet(
      documentSetType = CsvImportDocumentSet,
      uploadedFileId = Some(upload.uploadedFile.id)).save

    User.findById(upload.userId).map(documentSet.users.associate(_))
    documentSet.createDocumentSetCreationJob()
  }
}
 