package controllers

import java.util.UUID
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.Play.{start, stop}
import play.api.test.{FakeHeaders, FakeRequest, FakeApplication}
import play.api.test.Helpers._
import org.specs2.specification.Scope
import play.api.mvc.AnyContent
import play.api.mvc.SimpleResult
import play.api.test.Helpers._

import controllers.auth.AuthorizedRequest
import models.upload.{OverviewUpload,OverviewUploadedFile}
import models.OverviewUser
import models.orm.User

@RunWith(classOf[JUnitRunner])
class UploadControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  class TestUploadController(upload: Option[OverviewUpload] = None) extends UploadController {
    var uploadDeleted: Boolean = false
    var jobStarted: Boolean = false
    var lang: Option[String] = _
    var stopWords: Option[String] = _
    var importantWords: Option[String] = _

    def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
      Done(Right(mock[OverviewUpload]), Input.EOF)

    def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = upload
    def deleteUpload(upload: OverviewUpload) { uploadDeleted = true }
    def createDocumentSetCreationJob(upload: OverviewUpload, documentSetLanguage: String, suppliedStopWords: String, suppliedImportantWords:String) {
      jobStarted = true
      lang = Some(documentSetLanguage)
      stopWords = Some(suppliedStopWords)
      importantWords = Some(suppliedImportantWords)
    }
  }

  trait UploadContext[A] extends Scope {
    val guid = UUID.randomUUID

    def upload: OverviewUpload
    val controller: TestUploadController
    val request: Request[A]
    val result: Result
  }

  trait CreateRequest extends UploadContext[OverviewUpload] {
    val controller = new TestUploadController
    val request = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), upload, "controllers.UploadController.create")
    val result = controller.create(guid)(request)
  }

  trait HeadRequest extends UploadContext[AnyContent] {
    val user = OverviewUser(User(1l))
    val controller = new TestUploadController(Option(upload))
    val request = new AuthorizedRequest(FakeRequest(), user)
    val result = controller.show(guid)(request)
  }

  trait StartClusteringRequest extends UploadContext[AnyContent] {
    val user = OverviewUser(User(1l))
    val controller = new TestUploadController(Option(upload))
    val lang = "sv"
    val stopWords = "some stop words"
    val request = new AuthorizedRequest(FakeRequest()
        .withFormUrlEncodedBody(("lang" -> lang), ("supplied_stop_words" -> stopWords)), user)

    val result = controller.startClustering(guid)(request)
  }

  trait NoStartedUpload {
    def upload: OverviewUpload = null
  }

  trait StartedUpload {
    def contentDisposition = "attachment; filename=file.name"
    def bytesUploaded: Long

    def upload: OverviewUpload = {
      val u = mock[OverviewUpload]
      val f = mock[OverviewUploadedFile]
      u.uploadedFile returns f
      f.size returns bytesUploaded
      f.contentDisposition returns contentDisposition
      u.size returns 1000
    }
  }

  trait CompleteUpload extends StartedUpload {
    override def bytesUploaded: Long = 1000
  }

  trait IncompleteUpload extends StartedUpload {
    override def bytesUploaded: Long = 100
  }

  "UploadController.create" should {
    "return OK if upload is complete" in new CreateRequest with CompleteUpload {
      status(result) must be equalTo (OK)
    }

    "return BAD_REQUEST if upload is not complete" in new CreateRequest with IncompleteUpload {
      status(result) must be equalTo (BAD_REQUEST)
    }
  }

  "UploadController.startClustering" should {

    "start a DocumentSetCreationJob and delete the upload" in new StartClusteringRequest with CompleteUpload {
      status(result) must be equalTo(SEE_OTHER)
      controller.jobStarted must beTrue
      controller.lang must beSome(lang)
      controller.stopWords must beSome(stopWords)
      controller.uploadDeleted must beTrue
    }

    "not start a DocumentSetCreationJob if upload is not complete" in new StartClusteringRequest with IncompleteUpload {
      status(result) must be equalTo(CONFLICT)
      controller.jobStarted must beFalse
    }
  }


  "UploadController.show" should {
    "return NOT_FOUND if upload does not exist" in new HeadRequest with NoStartedUpload {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return OK with upload info in headers if upload is complete" in new HeadRequest with CompleteUpload {
      status(result) must be equalTo (OK)
      header(CONTENT_LENGTH, result) must beSome("1000")
      header(CONTENT_DISPOSITION, result) must beSome(contentDisposition)
    }

    "return PARTIAL_CONTENT with upload info in headers if upload is not complete" in new HeadRequest with IncompleteUpload {
      status(result) must be equalTo (PARTIAL_CONTENT)
      header(CONTENT_RANGE, result) must beSome("0-99/1000")
      header(CONTENT_DISPOSITION, result) must beSome(contentDisposition)
    }

    "return NOT_FOUND if upload is empty" in new HeadRequest with NoStartedUpload {
      status(result) must be equalTo (NOT_FOUND)
    }
  }


  step(stop)
}
