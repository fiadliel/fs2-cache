package org.lyranthe.fs2.cache

import cats._
import cats.implicits._
import cats.effect._
import fs2._
import fs2.async.mutable.Signal

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Cache {

  private[cache] def runStreamAndRepeatWithDelay[F[_], A](s: Stream[F, A],
                                                          d: FiniteDuration,
                                                          scheduler: Scheduler)(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, A] =
    s ++ scheduler.delay(s, d).repeat

  private[cache] def createAndRefreshSignal[F[_], A, B](
      initialValue: A,
      get: F[B],
      modify: B => A,
      scheduler: Scheduler,
      refreshInterval: FiniteDuration,
      retryInterval: FiniteDuration)(implicit F: Effect[F],
                                     ec: ExecutionContext): Stream[F, F[A]] = {
    def retryGet: Stream[F, Option[B]] =
      Stream
        .eval(F.shift.flatMap(_ => get.map(_.some).handleError(_ => none)))
        .onError(_ => scheduler.delay(retryGet, retryInterval))

    def refreshSignal(signal: Signal[F, A]): Stream[F, Unit] =
      runStreamAndRepeatWithDelay(retryGet, refreshInterval, scheduler).unNone
        .to(_.evalMap(modify andThen signal.set))

    def refreshSignalAsync(signal: Signal[F, A]) =
      Stream.emit(signal.get).concurrently(refreshSignal(signal))

    for {
      signal <- Stream.eval(async.signalOf(initialValue))
      cacheValue <- refreshSignalAsync(signal)
    } yield cacheValue
  }

  /** Create a cache which repeatedly refreshes a value, may have no value initially
    *
    * @param get Effect which provides a new value to the cache when run
    * @param refreshInterval The duration between cache refreshes
    * @param retryInterval The duration between retries when a refresh fails
    * @param scheduler The scheduler used for scheduling refreshes
    * @param ec The execution context on which to run effects
    * @tparam F The effect type
    * @tparam A The type of value stored in the cache
    * @return An effect which when run, provides the current value of the cache
    */
  def optional[F[_], A](get: F[A],
                        refreshInterval: FiniteDuration,
                        retryInterval: FiniteDuration,
                        scheduler: Scheduler)(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, F[Option[A]]] =
    createAndRefreshSignal(none[A],
                           get,
                           Some.apply[A],
                           scheduler,
                           refreshInterval,
                           retryInterval)

  /** Create a cache which repeatedly refreshes a value. The effect which
    * gets the initial value must return without error.
    *
    * @param get Effect which provides a new value to the cache when run
    * @param refreshInterval The duration between cache refreshes
    * @param retryInterval The duration between retries when a refresh fails
    * @param scheduler The scheduler used for scheduling refreshes
    * @param ec The execution context on which to run effects
    * @tparam F The effect type
    * @tparam A The type of value stored in the cache
    * @return An effect which when run, provides the current value of the cache
    */
  def required[F[_], A](get: F[A],
                        refreshInterval: FiniteDuration,
                        retryInterval: FiniteDuration,
                        scheduler: Scheduler)(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, F[A]] =
    Stream
      .eval(F.shift *> get)
      .flatMap(
        initialValue =>
          createAndRefreshSignal(initialValue,
                                 get,
                                 identity[A],
                                 scheduler,
                                 refreshInterval,
                                 retryInterval))
}
