inThisBuild(
  Seq(
    version := "0.1",
    scalaVersion := "2.12.4",
    addCompilerPlugin(
      "org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary)
  ))

lazy val `cache` =
  project
    .in(file("."))
    .settings(
      libraryDependencies += "co.fs2" %% "fs2-core" % "0.10.0-M8",
      initialCommands in console :=
        """
        |jline.TerminalFactory.get.init
        |
        |import fs2._
        |import cats._
        |import cats.implicits._
        |import cats.effect._
        |import org.lyranthe.fs2.cache._
        |import scala.concurrent.duration._
        |import java.util.concurrent.Executors
        |
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |implicit val scheduler: Scheduler =
        |  fs2.Scheduler.fromScheduledExecutorService(Executors.newSingleThreadScheduledExecutor)
      """.stripMargin
    )
