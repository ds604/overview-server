package org.overviewproject.database.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.{ Document, DocumentSearchResult, DocumentSet, File, SearchResult }


object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName (propertyName: String) = NamingConventionTransforms.snakify(propertyName) 
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)

  val searchResults = table[SearchResult]
  val documentSearchResults = table[DocumentSearchResult]
  val documents = table[Document]
  val documentSets = table[DocumentSet]
  val files = table[File]
  val fileTexts = table[FileText]
}