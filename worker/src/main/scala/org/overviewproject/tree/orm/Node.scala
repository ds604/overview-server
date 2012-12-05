package org.overviewproject.tree.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity

class Node(
    val id: Long,
    @Column("document_set_id") val documentSetId: Long,
    @Column("parent_id") val parentId: Option[Long],
    val description: String
    ) extends KeyedEntity[Long] {

}