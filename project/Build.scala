import sbt._
import Keys._
import play.Project._
import templemore.sbt.cucumber.CucumberPlugin
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import com.typesafe.sbt.SbtStartScript

object ApplicationBuild extends Build with ProjectSettings {
  override def settings = super.settings ++ Seq(
    EclipseKeys.skipParents in ThisBuild := false,
    scalaVersion := ourScalaVersion,
    resolvers ++= ourResolvers)

  val workerJavaOpts = "-Dlogback.configurationFile=workerdevlog.xml" +: {
    if (System.getProperty("datasource.default.url") == null) Seq("-Ddatasource.default.url=" + appDatabaseUrl)
    else Nil
  }
  
  val ourTestWithNoDbOptions = Seq(
    Tests.Argument("xonly"),
    Tests.Setup { loader =>
      // Load Logger so configurations happen in the right order
      loader.loadClass("org.slf4j.LoggerFactory")
        .getMethod("getLogger", loader.loadClass("java.lang.String"))
        .invoke(null, "ROOT")
    }
  )	

  val ourTestOptions = ourTestWithNoDbOptions ++ Seq(
    Tests.Setup { () =>
      System.setProperty("datasource.default.url", testDatabaseUrl)
      System.setProperty("logback.configurationFile", "logback-test.xml")
    },
    Tests.Setup(loader => ClearTestDatabase(loader)))

  val printClasspathTask = TaskKey[Unit]("print-classpath")
  val printClasspath = printClasspathTask <<= (fullClasspath in Runtime) map { classpath =>
    println(classpath.map(_.data).mkString(":"))
  }

  val messageBroker = Project("message-broker", file("message-broker"), settings =
    Defaults.defaultSettings ++ SbtStartScript.startScriptForClassesSettings ++ OverviewCommands.defaultSettings ++
      Seq(
        libraryDependencies ++= messageBrokerDependencies,
        printClasspath))

  val searchIndex = Project("search-index", file("search-index"), settings =
    Defaults.defaultSettings ++ SbtStartScript.startScriptForClassesSettings ++ OverviewCommands.defaultSettings).settings(
        libraryDependencies ++= searchIndexDependencies,
        Keys.fork := true,
        javaOptions in run <++= (baseDirectory) map { (d) => 
          Seq(
            "-Des.path.home=" + d,
            "-Xms1g", "-Xmx1g", "-Xss256k",
            "-XX:+UseParNewGC",  "-XX:+UseConcMarkSweepGC", "-XX:CMSInitiatingOccupancyFraction=75", "-XX:+UseCMSInitiatingOccupancyOnly",
            "-Djava.awt.headless=true",
            "-Delasticsearch",
            "-Des.foreground=yes"
          )
        },
       printClasspath)

  // Create a subProject with our common settings
  object OverviewProject extends OverviewCommands with OverviewKeys {
    def apply(name: String, dependencies: Seq[ModuleID],
              useSharedConfig: Boolean = true,
              theTestOptions: Seq[TestOption] = ourTestOptions) = {
      Project(name, file(name), settings =
        Defaults.defaultSettings ++
          defaultSettings ++
          SbtStartScript.startScriptForClassesSettings ++
          Seq(printClasspath) ++
          addUnmanagedResourceDirectory(useSharedConfig) ++
          Seq(
            libraryDependencies ++= dependencies,
            testOptions in Test ++= theTestOptions,
            scalacOptions ++= ourScalacOptions,
            logBuffered := false,
            parallelExecution in Test := false,
            sources in doc in Compile := List(),
            printClasspath))
    }

    // don't clean the database if it isn't being used in tests
    def withNoDbTests(name: String, dependencies: Seq[ModuleID], useSharedConfig: Boolean = true,
                      theTestOptions: Seq[TestOption] = ourTestWithNoDbOptions) = apply(name, dependencies, useSharedConfig, theTestOptions)

    private def addUnmanagedResourceDirectory(useSharedConfig: Boolean) =
      if (useSharedConfig) Seq(unmanagedResourceDirectories in Compile <+= baseDirectory { _ / "../worker-conf" })
      else Seq()
  }

  // Project definitions
  val common = OverviewProject("common", commonProjectDependencies, useSharedConfig = false)

  val workerCommon = OverviewProject.withNoDbTests("worker-common", workerCommonProjectDependencies, useSharedConfig = false)
    .dependsOn(common)
    
  val documentSetWorker = OverviewProject.withNoDbTests("documentset-worker", documentSetWorkerProjectDependencies)
    .settings(
      Keys.fork := true,
      javaOptions in run ++= workerJavaOpts,
      javaOptions in Test += "-Dlogback.configurationFile=logback-test.xml")
    .dependsOn(common, workerCommon)

  val worker = OverviewProject("worker", workerProjectDependencies).settings(
    Keys.fork := true,
    javaOptions in run ++=  workerJavaOpts,
    javaOptions in Test += "-Dlogback.configurationFile=logback-test.xml").dependsOn(workerCommon, common)

  val main = play.Project(appName, appVersion, serverProjectDependencies).settings(
    resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/").settings(
      CucumberPlugin.cucumberSettingsWithIntegrationTestPhaseIntegration: _*).configs(
        IntegrationTest).settings(
          Defaults.itSettings: _*).settings(
            scalacOptions ++= ourScalacOptions,
            templatesImport += "views.Magic._",
            requireJs ++= Seq(
              "bundle/DocumentCloudImportJob/new.js",
              "bundle/DocumentSet/index.js",
              "bundle/DocumentSet/show.js",
              "bundle/Document/show.js",
              "bundle/Welcome/show.js",
              "bundle/admin/ImportJob/index.js",
              "bundle/admin/User/index.js"),
            requireJsShim += "main.js",
            aggregate in Compile := true,
            parallelExecution in IntegrationTest := false,
            javaOptions in Test ++= Seq(
              "-Dconfig.file=conf/application-test.conf",
              "-Dlogger.resource=logback-test.xml",
              "-Ddb.default.url=" + testDatabaseUrl),
            javaOptions in IntegrationTest ++= Seq(
              "-Dconfig.file=conf/application-it.conf",
              "-Dlogger.resource=logback-test.xml",
              "-Ddb.default.url=" + testDatabaseUrl,
              "-Dsbt.ivy.home=" + sys.props("sbt.ivy.home"),
              "-Dsbt.boot.properties=" + sys.props("sbt.boot.properties"),
              "-Dplay.home=" + sys.props("play.home"),
              "-Ddatasource.default.url=" + testDatabaseUrl),
            Keys.fork in Test := true,
            aggregate in Test := false,
            testOptions in Test ++= ourTestOptions,
            logBuffered := false,
            Keys.fork in IntegrationTest := true,
            sources in doc in Compile := List(),
            printClasspath,
            aggregate in printClasspathTask := false).settings(
              if (scala.util.Properties.envOrElse("COMPILE_LESS", "true") == "false")
                lessEntryPoints := Nil
              else
                lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" * "*.less") // only compile .less files that aren't in subdirs
                ).dependsOn(common)

  val all = Project("all", file("all"))
    .aggregate(main, worker, documentSetWorker, workerCommon, common)
    .settings(
      aggregate in Test := false,
      test in Test <<= (test in Test in main)
        dependsOn (test in Test in worker)
        dependsOn (test in Test in documentSetWorker)
        dependsOn (test in Test in workerCommon)
        dependsOn (test in Test in common))

}
