package org.overviewproject.util

import java.sql.Connection
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.overviewproject.persistence.{ DocumentSetCleaner, PersistentDocumentSetCreationJob }
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType.CsvUpload

class JobRestarterSpec extends Specification with Mockito {
  implicit val unusedConnection: Connection = null

  class TestJob extends PersistentDocumentSetCreationJob {
    val jobType = CsvUpload
    val lang = "se"
    val suppliedStopWords = None
    val documentSetId = 1L
    val documentCloudUsername: Option[String] = None
    val documentCloudPassword: Option[String] = None
    val splitDocuments: Boolean = false
    val contentsOid: Option[Long] = None
    val sourceDocumentSetId: Option[Long] = None
    val fileGroupId: Option[Long] = None
    
    var state = InProgress
    var fractionComplete = 0.98
    var statusDescription: Option[String] = Some("Almost finished!")

    var updateCalled: Boolean = false

    def update = {
      updateCalled = true
    }

    def checkForCancellation {}
    def delete {}
    def observeCancellation(f: PersistentDocumentSetCreationJob => Unit) {}
  }

  "JobRestarter" should {

    "clean and restart jobs" in {
      val cleaner = mock[DocumentSetCleaner]
      val job = new TestJob

      val jobRestarter = new JobRestarter(cleaner)

      jobRestarter.restart(Seq(job))

      job.state must be equalTo (NotStarted)
      job.updateCalled must beTrue 

      there was one(cleaner).clean(job.documentSetId)
    }
  }
}
