package controllers.util

import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee
import play.api.mvc.RequestHeader
import play.api.http.HeaderNames._
import org.overviewproject.util.ContentDisposition
import play.api.mvc.Result

trait MassUploadFileIteratee {

  val storage: Storage

  def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] = {
    val fileGroup = storage.findCurrentFileGroup.get
    val info = RequestInformation(request)
    val initialUpload: Either[Result, GroupedFileUpload] =
      Right(storage.createUpload(fileGroup.id, info.contentType, info.filename, info.total))

    Iteratee.fold(initialUpload) { (upload, data) =>
      upload.right.map { u =>
        storage.appendData(u, data)
      }
    }
  }

  trait Storage {
    def findCurrentFileGroup: Option[FileGroup]
    def createUpload(fileGroupId: Long, contentType: String, filename: String, size: Long): GroupedFileUpload
    def appendData(upload: GroupedFileUpload, data: Array[Byte]): GroupedFileUpload
  }

  private case class RequestInformation(filename: String, contentType: String, start: Long, end: Long, total: Long)
  private object RequestInformation {
    def apply(request: RequestHeader): RequestInformation = {
      val contentType = request.headers.get(CONTENT_TYPE).get
      val contentDisposition = request.headers.get(CONTENT_DISPOSITION).get
      val contentRange = request.headers.get(CONTENT_RANGE).get
      val range = """(\d+)-(\d+)/(\d+)""".r // start-end/length
      val rangeMatch = range.findFirstMatchIn(contentRange).get
      val List(start, end, length) = rangeMatch.subgroups.take(3)

      RequestInformation(ContentDisposition.filename(contentDisposition).get, contentType,
        start.toLong, end.toLong, length.toLong)
    }
  }
}
