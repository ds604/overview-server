package org.overviewproject.clone

import overview.util.Progress.Progress
import overview.util.DocumentSetCreationJobStateDescription
import overview.util.DocumentSetCreationJobStateDescription._
import org.overviewproject.database.Database
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

/**
 * The Procedure trait enables the specification of blocks of code
 * inside steps. A step verifies that the Procedure has not been cancelled
 * before executing the block, and notifies observers when the step is complete.
 * 
 * Each step has parameters specifying the amount of progress being made and end 
 * state.
 * 
 * Nested steps within the same class could be handled with DynamicVariable.
 * Some progress parameter needs to be passed if steps are performed in different classes.
 */
trait Procedure {
  private var progressListeners: Seq[Progress => Unit] = Seq.empty

  /**
   *  If we want steps to be able to return values, we need to use Either to
   *  handle the case when the Procedure has been cancelled.
   *  It might be nice to parameterize the type of the Right returned.
   */
  def step[T](fraction: Double, state: DocumentSetCreationJobStateDescription)(block: => T): Either[T, Boolean] = {
    if (!isCancelled) {
      val result = block
      progressListeners.foreach(_(Progress(fraction, state)))
      Left(result)
    } else Right(false)
  }

  def stepInTransaction[T](fraction: Double, state: DocumentSetCreationJobStateDescription)(block: => T): Either[T, Boolean] =
    Database.inTransaction(step(fraction, state)(block))

  /** 
   * Set observers to be notified at the completion of each step.
   * We could also pass in the Procedure itself in the notification
   */
  def observeSteps(notificationFunctions: Seq[Progress => Unit]) {
    progressListeners = notificationFunctions
  }

  /** Subclasses decide how to determine if the Procedure is cancelled */
  protected def isCancelled: Boolean
}

// -------

import persistence.PersistentDocumentSetCreationJob

/**
 * DocumentSetCreationJobProcedure checks the state of the job
 * to determine cancellation state.
 */
trait DocumentSetCreationJobProcedure extends Procedure {
  val job: PersistentDocumentSetCreationJob

  override protected def isCancelled: Boolean = {
    // again, assume we're in a transaction (see comment below)
    job.update
    job.state == Cancelled
  }
}

// -------

/** 
 * Observer that updates the job state in the database after each 
 * step is complete.
 */
class JobProgressReporter(job: PersistentDocumentSetCreationJob) {
  def updateStatus(progress: Progress) {
    job.fractionComplete = progress.fraction
    job.statusDescription = Some(progress.status.toString)
    // Can't handle nested transactions yet, so assume we are always in a transaction
    job.update
    // Eventually we can do:
    // Database.inTransaction(job.update)
  }
}

// -------
import overview.util.Logger

/** 
 * Observer that Logs the status. Having access to the Procedure would allow
 * it to report that a job has been cancelled. 
 */
object JobProgressLogger {
  def apply(progress: Progress) {
    Logger.info("PROGRESS: %f%% done. %s, OK".format(progress.fraction * 100, progress.status.toString))
  }
}


// --------

/**
 * The Procedure for cloning a document set
 */
trait DocumentSetCloner extends DocumentSetCreationJobProcedure {

  type DocumentIdMap = Map[Long, Long]
  type NodeIdMap = Map[Long, Long]
  type TagIdMap = Map[Long, Long]

  val cloneDocuments: (Long, Long) => DocumentIdMap
  val cloneNodes: (Long, Long, DocumentIdMap) => NodeIdMap
  val cloneTags: (Long, Long) => TagIdMap

  val cloneDocumentProcessingErrors: (Long, Long) => Unit

  val cloneNodeDocuments: (DocumentIdMap, NodeIdMap) => Unit
  val cloneDocumentTags: (DocumentIdMap, TagIdMap) => Unit

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) {

    // We need the for comprehension to capture the values of the initial steps
    // Having to reference the 'left' explicitly is a bit ugly.
    // The alternative would be to not return values from a step, and instead 
    // nest the step scopes (which would be even uglier)
    for {
      documentIdMapping <- stepInTransaction(0.20, Saving)(cloneDocuments(sourceDocumentSetId, cloneDocumentSetId)).left
      nodeIdMapping <- stepInTransaction(0.45, Saving)(cloneNodes(sourceDocumentSetId, cloneDocumentSetId, documentIdMapping)).left
      tagIdMapping <- stepInTransaction(0.55, Saving)(cloneTags(sourceDocumentSetId, cloneDocumentSetId)).left
    } {
      stepInTransaction(0.65, Saving) {
        cloneDocumentProcessingErrors(sourceDocumentSetId, cloneDocumentSetId)
      }
      stepInTransaction(0.95, Saving) {
        cloneNodeDocuments(documentIdMapping, nodeIdMapping)
      }
      stepInTransaction(1.00, Done) {
        cloneDocumentTags(documentIdMapping, tagIdMapping)
      }
    }
  }

}


// -------

/** 
 * Implements specific cloning methods for each component
 * If we want to track progress within each step, then DocumentClone, NodeCloner, etc.
 * would also have to be Procedures that would be passed some parameter so progress
 * could be shared across objects.
 */
object CloneDocumentSet {

  def apply(sourceDocumentSetId: Long, cloneDocumentSetId: Long, cloneJob: PersistentDocumentSetCreationJob, progressObservers: Seq[Progress => Unit]) {
    val cloner = new DocumentSetCloner {
      override val job = cloneJob
      override val cloneDocuments = DocumentCloner.clone _
      override val cloneNodes = NodeCloner.clone _
      override val cloneTags = TagCloner.clone _

      override val cloneDocumentProcessingErrors = DocumentProcessingErrorCloner.clone _
      override val cloneNodeDocuments = NodeDocumentCloner.clone _
      override val cloneDocumentTags = DocumentTagCloner.clone _
    }
    cloner.observeSteps(progressObservers)
    cloner.clone(sourceDocumentSetId, cloneDocumentSetId)
  }
}