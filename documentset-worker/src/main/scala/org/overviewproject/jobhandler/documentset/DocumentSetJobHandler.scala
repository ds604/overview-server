package org.overviewproject.jobhandler.documentset

import scala.concurrent.duration._

import akka.actor._
import akka.actor.SupervisorStrategy._

import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol.DeleteDocumentSet
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.messagequeue.{ AcknowledgingMessageReceiver, MessageService }
import org.overviewproject.messagequeue.MessageHandlerProtocol._
import org.overviewproject.messagequeue.apollo.ApolloMessageService
import org.overviewproject.searchindex.ElasticSearchComponents
import org.overviewproject.util.Configuration

import javax.jms._

trait Command

/**
 * Messages the JobHandler can process
 */
object DocumentSetJobHandlerProtocol {
  // Internal messages that should really be private, but are 
  // public for easier testing. 
  case class SearchCommand(documentSetId: Long, query: String) extends Command
  case class DeleteCommand(documentSetId: Long) extends Command
}

/**
 * `JobHandler` goes through the following state transitions:
 * NotConnected -> Ready: when StartListening has been received and a connection has been established
 * Ready -> WaitingForCompletion: when a message has been received and sent of to a handler
 * WaitingForCompletion -> Ready: when the command handler is done
 * <all states> -> NotConnected: when connection fails
 */
object DocumentSetJobHandlerFSM {
  sealed trait State
  case object Ready extends State
  case object WaitingForCompletion extends State

  // No data is kept
  sealed trait Data
  case object Working extends Data
}

/**
 * Component for creating a SearchHandler actor
 */
trait SearchComponent {
  val actorCreator: ActorCreator

  trait ActorCreator {
    def produceSearchHandler: Actor
    def produceDeleteHandler: Actor
  }
}

import DocumentSetJobHandlerFSM._

class DocumentSetMessageHandler extends Actor with FSM[State, Data] {
  this: SearchComponent =>

  import DocumentSetJobHandlerProtocol._

  override val supervisorStrategy =
    OneForOneStrategy(0, Duration.Inf) {
      case _: Exception => Stop
      case _: Throwable => Escalate
    }

  startWith(Ready, Working)

  when(Ready) {
    case Event(SearchCommand(documentSetId, query), _) => {
      val searchHandler = context.actorOf(Props(actorCreator.produceSearchHandler))
      context.watch(searchHandler)

      searchHandler ! SearchDocumentSet(documentSetId, query)
      goto(WaitingForCompletion)
    }
    case Event(DeleteCommand(documentSetId), _) => {
      val deleteHandler = context.actorOf(Props(actorCreator.produceDeleteHandler))
      context.watch(deleteHandler)

      deleteHandler ! DeleteDocumentSet(documentSetId)
      goto(WaitingForCompletion)
    }
  }

  when(WaitingForCompletion) {
    case Event(JobDone(documentSetId), _) => {
      context.unwatch(sender)
      context.parent ! MessageHandled
      goto(Ready)
    }
    case Event(Terminated(a), _) => {
      context.parent ! MessageHandled
      goto(Ready)
    }
  }

  initialize
}

/** Create a SearchHandler */
trait SearchComponentImpl extends SearchComponent {
  class ActorCreatorImpl extends ActorCreator {
    override def produceSearchHandler: Actor = new SearchHandler with SearchIndexAndSearchStorage

    override def produceDeleteHandler: Actor = new DeleteHandler with ElasticSearchComponents {
      override val documentSetDeleter = DocumentSetDeleter()
    }
  }
}

class DocumentSetMessageHandlerImpl extends DocumentSetMessageHandler with SearchComponentImpl {
  override val actorCreator = new ActorCreatorImpl
}

class DocumentSetJobHandler(messageService: MessageService) extends AcknowledgingMessageReceiver[Command](messageService) {
  override def createMessageHandler: Props = Props[DocumentSetMessageHandlerImpl]
  override def convertMessage(message: String): Command = ConvertDocumentSetMessage(message)

}

object DocumentSetJobHandler {
  private val messageService =
    new ApolloMessageService(Configuration.messageQueue.getString("queue_name"), Session.CLIENT_ACKNOWLEDGE)

  def apply(): Props = Props(new DocumentSetJobHandler(messageService))
}
