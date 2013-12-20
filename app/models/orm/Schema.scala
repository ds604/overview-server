package models.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.NodeDocument

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName(propertyName: String) =
    NamingConventionTransforms.snakify(propertyName)

  override def tableNameFromClassName(className: String) =
    NamingConventionTransforms.snakify(className)

  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSearchResults = table[DocumentSearchResult]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val documentSets = table[DocumentSet]
  val documentSetUsers = table[DocumentSetUser]
  val documents = table[Document]
  val documentTags = table[DocumentTag]
  val fileGroups = table[FileGroup]
  val groupedFileUploads = table[GroupedFileUpload]
  val logEntries = table[LogEntry]
  val nodeDocuments = table[NodeDocument]
  val nodes = table[Node]
  val searchResults = table[SearchResult]
  val tags = table[Tag]
  val uploadedFiles = table[UploadedFile]
  val uploads = table[Upload]
  val users = table[User]

  on(documents)(d => declare(d.id is(primaryKey)))
  on(nodes)(n => declare(n.id is(primaryKey)))
}
