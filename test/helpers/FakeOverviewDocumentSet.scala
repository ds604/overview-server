package helpers

import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob }

case class FakeOverviewDocumentSet(
    id: Long = 1l, 
    title: String = "a title",
    query: String = "a query", 
    isPublic: Boolean = false,
    creationJob: Option[OverviewDocumentSetCreationJob] = None, 
    errorCount: Int = 0) extends OverviewDocumentSet {

  val user = null
  val createdAt = null
  val documentCount = 15

  def cloneForUser(cloneOwnerId: Long): OverviewDocumentSet = this
}
