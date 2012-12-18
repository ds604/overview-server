package models

import java.sql.Timestamp
import org.overviewproject.test.Specification
import org.specs2.specification.Scope
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import models.orm.{ DocumentSet, UploadedFile }
import models.orm.DocumentSetType._
import models.upload.OverviewUploadedFile
import helpers.DbTestContext
import models.orm.Schema
import models.orm.DocumentSetUser

class OverviewDocumentSetSpec extends Specification {
  step(start(FakeApplication()))

  "OverviewDocumentSet" should {
    trait OneDocumentSet {
      def throwWrongType = throw new Exception("Wrong DocumentSet type")
      def ormDocumentSet: DocumentSet

      lazy val documentSet = OverviewDocumentSet(ormDocumentSet)
    }

    trait CsvImportDocumentSetScope extends Scope with OneDocumentSet {
      val ormUploadedFile = UploadedFile(
        id = 0L,
        uploadedAt = new java.sql.Timestamp(new java.util.Date().getTime()),
        contentsOid = 0L,
        contentDisposition = "attachment; filename=foo.csv",
        contentType = "text/csv; charset=latin1",
        size = 0L)

      val title = "Title"
      val createdAt = new java.util.Date()
      val count = 10

      override def ormDocumentSet = DocumentSet(
        CsvImportDocumentSet,
        title = title,
        createdAt = new java.sql.Timestamp(createdAt.getTime()),
        uploadedFile = Some(ormUploadedFile),
        providedDocumentCount = Some(count))
    }

    trait DocumentCloudDocumentSetScope extends Scope with OneDocumentSet {
      val title = "Title"
      val query = "Query"
      val createdAt = new java.util.Date()
      val count = 10

      override def ormDocumentSet = DocumentSet(
        DocumentCloudDocumentSet,
        title = title,
        query = Some(query),
        createdAt = new java.sql.Timestamp(createdAt.getTime()),
        providedDocumentCount = Some(count))
    }

    "apply() should generate a CsvImportDocumentSet" in new CsvImportDocumentSetScope {
      documentSet must beAnInstanceOf[OverviewDocumentSet.CsvImportDocumentSet]
    }

    "apply() should generate a DocumentCloudDocumentSet" in new DocumentCloudDocumentSetScope {
      documentSet must beAnInstanceOf[OverviewDocumentSet.DocumentCloudDocumentSet]
    }

    "createdAt should point to the ORM document" in new CsvImportDocumentSetScope {
      documentSet.createdAt.getTime must beEqualTo(createdAt.getTime)
    }

    "title should be the title" in new DocumentCloudDocumentSetScope {
      documentSet.title must beEqualTo(title)
    }

    "documentCount should be the document count" in new DocumentCloudDocumentSetScope {
      documentSet.documentCount must beEqualTo(count)
    }

    "CSV document sets must have an uploadedFile" in new CsvImportDocumentSetScope {
      documentSet match {
        case csvDs: OverviewDocumentSet.CsvImportDocumentSet => {
          csvDs.uploadedFile must beAnInstanceOf[Some[OverviewUploadedFile]]
        }
        case _ => throwWrongType
      }
    }

    "DC document sets must have a query" in new DocumentCloudDocumentSetScope {
      documentSet match {
        case dcDs: OverviewDocumentSet.DocumentCloudDocumentSet => {
          dcDs.query must beEqualTo(query)
        }
        case _ => throwWrongType
      }
    }
  }

  "OverviewDocumentSet database operations" should {

    import anorm.SQL
    import anorm.SqlParser._
    import org.squeryl.PrimitiveTypeMode._
    import models.orm.Schema._
    import models.orm.{ DocumentSet, DocumentTag, LogEntry, Tag, User }
    import org.overviewproject.postgres.LO
    import org.overviewproject.tree.orm.{ Document, Node, DocumentSetCreationJob }
    import org.overviewproject.tree.orm.DocumentType._

    trait DocumentSetWithUserScope extends DbTestContext {

      var admin: User = _
      var ormDocumentSet: DocumentSet = _
      var documentSet: OverviewDocumentSet = _
      // Will become cleaner when OverviewDocumentSet is cleared up
      override def setupWithDb = {
        admin = User.findById(1l).getOrElse(throw new Exception("Missing admin user from db"))
        ormDocumentSet = admin.createDocumentSet("query").save
        documentSet = OverviewDocumentSet(ormDocumentSet)
      }
    }

    trait DocumentSetReferencedByOtherTables extends DocumentSetWithUserScope {

      var oid: Long = _

      override def setupWithDb = {
        super.setupWithDb
        LogEntry(documentSetId = documentSet.id,
          userId = 1l,
          date = new Timestamp(0),
          component = "test").save
        val document = documents.insertOrUpdate(Document(CsvImportDocument, documentSet.id, text = Some("test doc")))
        val tag = Tag(documentSetId = documentSet.id, name = "tag").save
        val node = nodes.insertOrUpdate(Node(documentSet.id, None, "description", 10, Array.empty))
        documentTags.insertOrUpdate(DocumentTag(document.id, tag.id))
        documentSetCreationJobs.insertOrUpdate(DocumentSetCreationJob(documentSet.id))

        SQL("INSERT INTO node_document (node_id, document_id) VALUES ({n}, {d})").on("n" -> node.id, "d" -> document.id).executeInsert()
      }
    }

    "user should be the user" in new DocumentSetWithUserScope {
      val d = OverviewDocumentSet.findById(documentSet.id).get
      d.user.id must be equalTo (1l)
      d.user.email must be equalTo ("admin@overview-project.org")
    }

    "delete all associated information" in new DocumentSetReferencedByOtherTables {
      OverviewDocumentSet.delete(documentSet.id)

      logEntries.allRows must have size (0)
      documents.allRows must have size (0)
      tags.allRows must have size (0)
      nodes.allRows must have size (0)
      documentTags.allRows must have size (0)
      documentSetCreationJobs.allRows must have size (0)

      SQL("SELECT * FROM node_document").as(long("node_id") ~ long("document_id") map flatten *) must have size (0)
    }
  }
  step(stop)
}
