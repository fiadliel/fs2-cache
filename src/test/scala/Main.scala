package org.lyranthe.fs2.cache

import cats.effect.IO
import fs2._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

object Main {
  def print[A]: Sink[IO, A] =
    _.evalMap(v => IO(println(v)))

  def main(args: Array[String]): Unit = {
    val values = for {
      scheduler <- Scheduler[IO](2)
      cache <- Cache.required(IO(Random.nextInt),
                              10.seconds,
                              10.seconds,
                              scheduler)
      value <- scheduler.delay(Stream.eval(cache), 3.seconds).repeat
    } yield value

    values.take(20).to(print).run.unsafeRunSync()
  }
}
