package org.overviewproject.runner

import java.io.{ByteArrayOutputStream,File,FilterOutputStream,OutputStream}
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

class BiOutputStream(out1: OutputStream, out2: OutputStream) extends FilterOutputStream(out1) {
  override def write(b: Int): Unit = {
    super.write(b)
    out2.write(b)
  }

  override def write(b: Array[Byte]): Unit = {
    super.write(b)
    out2.write(b)
  }

  override def write(b: Array[Byte], off: Int, len: Int) : Unit = {
    super.write(b, off, len)
    out2.write(b, off, len)
  }

  override def flush(): Unit = {
    super.flush()
    out2.flush()
  }

  override def close(): Unit = {
    super.close()
    out2.close()
  }
}

case class DaemonSpec(
    key: String,
    colorCode: String,
    env: Seq[(String,String)], 
    jvmArgs: Seq[String],
    args: Seq[String]) {
}

class Main(args: Array[String]) {
  lazy val logger = new Logger(System.out, System.err)

  lazy val daemonSpecs = Seq[DaemonSpec](
    DaemonSpec(
      "worker",
      Console.MAGENTA,
      Seq(),
      Seq(),
      Seq("JobHandler")
    )
  )

  /** Returns classpaths, one per daemonSpec, in the same order as daemonSpecs.
   */
  def getClasspaths() : Seq[String] = {
    logger.out.println("Compiling and fetching...")

    val cpStream = new ByteArrayOutputStream()
    val cpLogger = new Logger(new BiOutputStream(System.out, cpStream), System.err)
    val sublogger = cpLogger.sublogger("sbt", Some(Console.BLUE.getBytes()))

    val sbtTasks = daemonSpecs.map((spec: DaemonSpec) => s"show ${spec.key}/full-classpath")
    val sbtCommand = (Seq("", "all/compile") ++ sbtTasks).mkString("; ")

    val sbtLaunchUrl = getClass.getResource("/sbt-launch.jar")

    val sbtRun = new Daemon(sublogger.toProcessLogger, Seq(),
      Seq(
        "-Dsbt.log.format=false"
      ),
      Seq(
        "-jar", new File(sbtLaunchUrl.toURI()).getAbsolutePath(),
        sbtCommand
      )
    )
    val statusCode = Await.result(sbtRun.statusCodeFuture, Duration.Inf)

    if (statusCode != 0) {
      cpLogger.err.println(s"sbt exited with code ${statusCode}. Please fix the error.")
      System.exit(statusCode)
    }

    // Find lines like [info] List(Attributed(path1), Attributed(path2), ...).
    // There will be one line per "show KEY/full-classpath" command.
    // Parse each one and return path1:path2:...
    val LinePattern = """\[info\] List\((.*)\)""".r
    val PathPattern = """Attributed\(([^\)]+)\)""".r
    val outputString = new String(cpStream.toByteArray())
    System.out.println("OUTPUT: " + outputString)
    LinePattern.findAllMatchIn(outputString).map(_.group(1))
      .map { line => PathPattern.findAllMatchIn(line).map(_.group(1)).mkString(":") }
      .toSeq
  }

  def run() = {
    def makeDaemon(spec: DaemonSpec, classpath: String) : Daemon = {
      new Daemon(
        logger.sublogger(spec.key, Some(spec.colorCode.getBytes())).toProcessLogger,
        spec.env,
        spec.jvmArgs ++ Seq("-cp", classpath),
        spec.args
      )
    }

    val classpaths = getClasspaths()
    val daemons = daemonSpecs.zip(classpaths).map { case (spec, classpath) => makeDaemon(spec, classpath) }

    import scala.concurrent.ExecutionContext.Implicits.global
    val future = Future.sequence(daemons.map(_.statusCodeFuture))
    val statusCodes = Await.result(future, Duration.Inf)

    logger.out.println(s"Exited successfully! Status codes: ${statusCodes.mkString(", ")}")
  }
}

object Main {
  def main(args: Array[String]) : Unit = new Main(args).run()
}
