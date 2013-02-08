package models

import org.overviewproject.tree.orm.{ Document, DocumentSetCreationJob }
import org.overviewproject.tree.orm.DocumentSetCreationJobType._
import models.orm.DocumentSet
import models.upload.OverviewUploadedFile
import models.orm.User

trait OverviewDocumentSet {
  /** database ID */
  val id: Long

  /*
   * XXX we don't have a "list documents" method. To include one, we'd need
   * an API for filtering and pagination--wrappers around Squeryl features.
   */

  /**
   * Creation job, if this DocumentSet isn't complete yet.
   * FIXME: should be models.OverviewDocumentSetCreationJob, but we don't have one
   */
  def creationJob: Option[OverviewDocumentSetCreationJob]

  /**
   * Number of documents.
   *
   * If the DocumentSet hasn't finished being generated, this number may be
   * less than its final value.
   */
  def documentCount: Int

  /**
   * Number of documents that could not be processed because of errors. May change over time.
   */
  def errorCount: Int
  
  /** true if the document set is public */
  val isPublic: Boolean

  /** Title of the document set. (Empty string is allowed.) */
  val title: String

  /** Creation date (without milliseconds) */
  val createdAt: java.util.Date

  /** The user owning the document set */
  val user: OverviewUser

  /** FIXME: Only here because admin page expects it of all jobs */
  val query: String

  /**
   * @return a new OverviewDocumentSet owned by cloneOwner. Creates a OverviewDocumentSetCreationJob
   * that will create a copy of the original, including nodes, tags, and documents.
   */
  def cloneForUser(cloneOwnerId: Long): OverviewDocumentSet
}

object OverviewDocumentSet {
  trait OverviewDocumentSetImpl extends OverviewDocumentSet {
    protected val ormDocumentSet: DocumentSet

    override val id = ormDocumentSet.id
    override lazy val creationJob = OverviewDocumentSetCreationJob.findByDocumentSetId(id)
    override lazy val documentCount = ormDocumentSet.documentCount.toInt
    override lazy val errorCount = ormDocumentSet.errorCount.toInt
    override val isPublic = ormDocumentSet.isPublic
    override val title = ormDocumentSet.title
    override val createdAt = ormDocumentSet.createdAt
    override lazy val user = {
      OverviewUser.findById(ormDocumentSet.users.single.id).get
    }
    override lazy val query = ""

    override def cloneForUser(cloneOwnerId: Long): OverviewDocumentSet = {
      import models.orm.Schema
      val ormDocumentSetClone = cloneDocumentSet.save

      User.findById(cloneOwnerId).map(u => ormDocumentSetClone.users.associate(u))

      val cloneJob = DocumentSetCreationJob(documentSetCreationJobType = CloneJob, documentSetId = ormDocumentSetClone.id, sourceDocumentSetId = Some(ormDocumentSet.id))
      Schema.documentSetCreationJobs.insert(cloneJob)
      OverviewDocumentSet(ormDocumentSetClone)
    }

    protected def cloneDocumentSet: DocumentSet = ormDocumentSet.copy(id = 0, isPublic = false) 
  }

  case class CsvImportDocumentSet(protected val ormDocumentSet: DocumentSet) extends OverviewDocumentSetImpl {
    lazy val uploadedFile: Option[OverviewUploadedFile] = 
      ormDocumentSet.uploadedFile.map(OverviewUploadedFile.apply)

    override protected def cloneDocumentSet: DocumentSet = {
      val ormDocumentSetClone = super.cloneDocumentSet
      val uploadedFileClone = ormDocumentSet.withUploadedFile.uploadedFile.map(f => OverviewUploadedFile(f.copy()).save)
      ormDocumentSetClone.copy(uploadedFileId = uploadedFileClone.map(_.id))
    }
  }

  case class DocumentCloudDocumentSet(protected val ormDocumentSet: DocumentSet) extends OverviewDocumentSetImpl {
    private def throwOnNull = throw new Exception("DocumentCloudDocumentSet has NULL values it should not have")

    override lazy val query: String = ormDocumentSet.query.getOrElse(throwOnNull)
  }

  /** Factory method */
  def apply(ormDocumentSet: DocumentSet): OverviewDocumentSet = {
    ormDocumentSet.documentSetType.value match {
      case "CsvImportDocumentSet" => CsvImportDocumentSet(ormDocumentSet)
      case "DocumentCloudDocumentSet" => DocumentCloudDocumentSet(ormDocumentSet)
      case _ => throw new Exception("Impossible document-set type " + ormDocumentSet.documentSetType.value)
    }
  }

  /** Database lookup */
  def findById(id: Long): Option[OverviewDocumentSet] = {
    DocumentSet.findById(id).map({ ormDocumentSet =>
      OverviewDocumentSet(ormDocumentSet.withUploadedFile.withCreationJob)
    })
  }
  
  /** @return Seq of all document sets marked public at the time of the call */
  def findPublic: Seq[OverviewDocumentSet] = {
    import models.orm.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._

    Schema.documentSets.where(d => d.isPublic === true).map(OverviewDocumentSet(_)).toSeq
  }

  def delete(id: Long) {
    import models.orm.Schema._
    import org.overviewproject.postgres.SquerylEntrypoint._
    import org.overviewproject.tree.orm.DocumentSetCreationJobState._

    deleteClientGeneratedInformation(id)
    val cancelledJob = OverviewDocumentSetCreationJob.cancelJobWithDocumentSetId(id)
    if (!cancelledJob.isDefined) deleteClusteringGeneratedInformation(id)
  }

  private def deleteClientGeneratedInformation(id: Long) {
    import models.orm.Schema._
    import org.overviewproject.postgres.SquerylEntrypoint._

    logEntries.deleteWhere(le => le.documentSetId === id)
    documentTags.deleteWhere(nt =>
      nt.tagId in from(tags)(t => where(t.documentSetId === id) select (t.id)))
    tags.deleteWhere(t => t.documentSetId === id)
    documentSetUsers.deleteWhere(du => du.documentSetId === id)
  }

  private def deleteClusteringGeneratedInformation(id: Long) = {
    import anorm._
    import anorm.SqlParser._
    import models.orm.Schema._
    import org.overviewproject.postgres.SquerylEntrypoint._
    implicit val connection = OverviewDatabase.currentConnection

    SQL("SELECT lo_unlink(contents_oid) FROM document_set_creation_job WHERE document_set_id = {id} AND contents_oid IS NOT NULL").on('id -> id).as(scalar[Int] *)

    documentSetCreationJobs.deleteWhere(dscj => dscj.documentSetId === id)

    SQL("""
        DELETE FROM node_document WHERE node_id IN (
          SELECT id FROM node WHERE document_set_id = {id}
        )""").on('id -> id).executeUpdate()

    documents.deleteWhere(d => d.documentSetId === id)
    documentProcessingErrors.deleteWhere(dpe => dpe.documentSetId === id)
    nodes.deleteWhere(n => n.documentSetId === id)

    // use headOption rather than single to handle case where uploadedFileId is deleted already
    // flatMap identity to transform from Option[Option[Long]] to Option[Long]
    val uploadedFileId = from(documentSets)(d => where(d.id === id) select (d.uploadedFileId)).headOption.flatMap(identity)
    documentSets.delete(id)

    uploadedFileId.map { uid => uploadedFiles.deleteWhere(f => f.id === uid) }

  }
}
