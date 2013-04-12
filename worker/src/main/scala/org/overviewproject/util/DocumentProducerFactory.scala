/*
 * DocumentProducerFactory.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.util

import org.overviewproject.clustering.DocumentCloudSource
import org.overviewproject.http.{ AsyncHttpRetriever, DocumentCloudDocumentProducer }
import org.overviewproject.util.Progress._
import org.overviewproject.csv.CsvImportDocumentProducer
import org.overviewproject.persistence.{ DocumentSet, PersistentDocumentSetCreationJob }
import org.overviewproject.http.AsyncHttpRequest
import org.overviewproject.http.Credentials


/** Common functionality for DocumentProducers */
trait DocumentProducer {
  /**
   * Produce the documents. There should probably be some restrictions
   * here to indicate that we're producing documents and feeding them
   * to DocumentConsumers.
   */
  def produce()
}

/** A consumer of documents */
trait DocumentConsumer {
  /** How the document text is received, along with a document id */
  def processDocument(documentId: Long, text: String)

  /** Called on the consumer when no more documents will be generated */
  def productionComplete()
}

/**
 * Factory for generating a DocumentProducer based on the documentSet.
 * Depending on the documentSet type either a DocumentCloudDocumentProducer
 * or a CsvImportDocumentProducer is generated.
 */
object DocumentProducerFactory {
  /** The maximum number of documents processed for a document set */
  private val MaxDocuments = 20000
  
  /** Return a DocumentProducer based on the DocumentSet type */
  def create(documentSetCreationJob: PersistentDocumentSetCreationJob, documentSet: DocumentSet, consumer: DocumentConsumer,
    progAbort: ProgressAbortFn): DocumentProducer = documentSet.documentSetType match {
    case "DocumentCloudDocumentSet" =>
      val credentials = for {
        username <- documentSetCreationJob.documentCloudUsername
        password <- documentSetCreationJob.documentCloudPassword
      } yield Credentials(username, password)
      
      new DocumentCloudDocumentProducer(documentSetCreationJob.documentSetId, documentSet.query.get, credentials, MaxDocuments, consumer, progAbort)
    case "CsvImportDocumentSet" =>
      new CsvImportDocumentProducer(documentSetCreationJob.documentSetId, documentSetCreationJob.contentsOid.get, documentSet.uploadedFileId.get, consumer, MaxDocuments, progAbort)
  }
}