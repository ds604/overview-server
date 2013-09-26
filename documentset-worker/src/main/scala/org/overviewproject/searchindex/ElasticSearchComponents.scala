package org.overviewproject.searchindex

import scala.concurrent.{ Future, Promise }
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.{ SearchResponse, SearchType }
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders
import org.overviewproject.jobhandler.documentset.SearchIndex
import org.overviewproject.jobhandler.documentset.SearcherComponents
import org.overviewproject.util.Logger
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.overviewproject.util.Configuration

trait ElasticSearchComponents extends SearcherComponents {

  private class ActionResult[A] extends ActionListener[A] {

    private val p: Promise[A] = Promise()

    def resultFuture: Future[A] = p.future

    override def onResponse(response: A): Unit = {
      p.success(response)
    }

    override def onFailure(failure: Throwable): Unit = {
      p.failure(failure)
    }
  }

  class ElasticSearchIndex extends SearchIndex {

    private val IndexName = Configuration.searchIndex.indexName
    
    private val PageSize = 100
    private val ScrollTime = new TimeValue(60000)
    private val SearchableFields = Seq("text", "title", "supplied_id")

    private val client = ElasticSearchClient.client

    override def startSearch(index: String, queryString: String): Future[SearchResponse] = {
      Logger.info(s"Starting query:[$index] $queryString")
      val query = QueryBuilders.multiMatchQuery(queryString, SearchableFields: _*)

      val listener = new ActionResult[SearchResponse]()

      client.prepareSearch(index)
        .setSearchType(SearchType.SCAN)
        .setScroll(ScrollTime)
        .setQuery(query)
        .setSize(PageSize)
        .execute(listener)

      listener.resultFuture
    }

    override def getNextSearchResultPage(scrollId: String): Future[SearchResponse] = {
      val listener = new ActionResult[SearchResponse]()

      client.prepareSearchScroll(scrollId)
        .setScroll(ScrollTime)
        .execute(listener)

      listener.resultFuture
    }

    override def deleteDocuments(documentSetId: Long): Future[DeleteByQueryResponse] = {
      Logger.debug(s"Deleting documents with document_set_id: $documentSetId")
      val listener = new ActionResult[DeleteByQueryResponse]
      val query = QueryBuilders.termQuery("document_set_id", documentSetId)
      client.prepareDeleteByQuery(IndexName)
        .setQuery(query)
        .execute(listener)

      listener.resultFuture
    }

    override def deleteDocumentSetAlias(documentSetId: Long): Future[IndicesAliasesResponse] = {
      Logger.debug(s"Deleting alias for document_set_id: $documentSetId")
      val listener = new ActionResult[IndicesAliasesResponse]
      val adminClient = client.admin.indices
      adminClient.prepareAliases.
        removeAlias(IndexName, s"documents_$documentSetId")
        .execute(listener)

      listener.resultFuture
    }
  }

  override val searchIndex: SearchIndex = new ElasticSearchIndex

}