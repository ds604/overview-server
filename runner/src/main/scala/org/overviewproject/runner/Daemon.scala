package org.overviewproject.runner

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessLogger}

class Daemon(
    logger: ProcessLogger,
    env: Seq[(String,String)] = Seq(),
    jvmArgs: Seq[String] = Seq(),
    args: Seq[String] = Seq()) {

  val statusCodeFuture : Future[Int] = Future({
    Daemon.runJavaSync(logger, env, jvmArgs, args)
  })(ExecutionContext.global)
}

object Daemon {
  private val javaPath: String = {
    val home = new File(System.getProperty("java.home"))
    new File(new File(home, "bin"), "java").getAbsolutePath
  }

  private def runJavaSync(
      logger: ProcessLogger,
      env: Seq[(String,String)],
      jvmArgs: Seq[String],
      args: Seq[String]
      ) : Int = {
    val commandSeq = Seq(javaPath) ++ jvmArgs ++ args

    logger.out("Running " + commandSeq.map(x => s"'${x}'").mkString(" "))

    val processBuilder = Process(commandSeq, None, env : _*)
    val process = processBuilder.run(logger)
    process.exitValue()
  }
}
