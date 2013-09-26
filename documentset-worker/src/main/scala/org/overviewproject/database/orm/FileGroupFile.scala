package org.overviewproject.database.orm

import org.overviewproject.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

case class FileGroupFile(
    fileGroupId: Long,
    fileId: Long) extends KeyedEntity[CompositeKey2[Long, Long]] {
  override def id = compositeKey(fileGroupId, fileId)
}