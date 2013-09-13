package org.overviewproject.jobhandler.filegroup

import akka.actor._

import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol.ListenForFileGroupJobs



object MotherWorkerProtocol {
  sealed trait Command
  case class StartClusteringCommand(fileGroupId: Long) extends Command
}

trait FileGroupJobHandlerComponent {
  def createFileGroupJobHandler: Props
}

class MotherWorker extends Actor {
  this: FileGroupJobHandlerComponent =>
    
  private val fileGroupJobHandlers: Seq[ActorRef] = for (i <- 1 to 2) yield {
    val handler = context.actorOf(createFileGroupJobHandler)
    handler ! ListenForFileGroupJobs
    
    handler
  }
    
  

  def receive = {
    case _ =>
  }
}