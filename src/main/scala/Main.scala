/**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package com.madewithtea.blockchainimporter

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.effect._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import com.typesafe.scalalogging.LazyLogging

import com.madewithtea.blockchainrpc.{Config, RPCClient}

import cats.implicits._
import sinks.Instances._
import persistence.Instances._
import strategies.Instances._
import bitcoin.Instances._

import strategies.Utils._

object Main extends IOApp with LazyLogging {
  implicit val ec = global

  def run(args: List[String]): IO[ExitCode] = {

    val config = (Env.blockchain, Env.mode, Env.environment)
    logger.info(s"Running extractor for ${config}")
    val l = (Env.blockchain, Env.mode, Env.environment) match {
      // bitcoin
      case (b: Blockchain.Bitcoin, m: Mode.Lagging, e: Environment.Local) =>
        loop(b, m,  Resources.local[Blockchain.Bitcoin]())
      case (b: Blockchain.Bitcoin, m: Mode.Push, e: Environment.Local) =>
        loop(b, m,  Resources.local[Blockchain.Bitcoin]())
      case (b: Blockchain.Bitcoin, m: Mode.PushAndVerify, e: Environment.Local) =>
        loop(b, m,  Resources.local[Blockchain.Bitcoin]())
      case (b: Blockchain.Bitcoin, m: Mode.Lagging, e: Environment.Production) =>
        loop(b, m,  Resources.production[Blockchain.Bitcoin]())
      case (b: Blockchain.Bitcoin, m: Mode.Push, e: Environment.Production) =>
        loop(b, m,  Resources.production[Blockchain.Bitcoin]())
      case (b: Blockchain.Bitcoin, m: Mode.PushAndVerify, e: Environment.Production) =>
        loop(b, m, Resources.production[Blockchain.Bitcoin]())
      case _ => throw new Exception("Could not instantiate configuration")
    }

    l.flatMap(_ => IO(ExitCode(0)))
      .handleErrorWith {
        case ex: Exception =>
          for {
            _ <- IO(logger.error(ex.getMessage()))
          } yield ExitCode(-1)
      }
  }
}

object Env {
  val blockchain: Blockchain = Context.blockchain match {
    // @todo use case object instead of case class 
    case "bitcoin" => Blockchain.Bitcoin()
    case "omni"    => Blockchain.Omni()
    case _         => throw new Exception("Blockchain not supported")
  }

  val mode: Mode = Context.orThrow("MODE") match {
    case "lagging" =>
      Mode.Lagging(
        Context.orThrow("LAG_BEHIND").toInt,
        FiniteDuration.apply(Context.orThrow("POLL_TIME_MS").toLong, "ms")
      )
    case "push" => Mode.Push()
    case "pushverify" => Mode.PushAndVerify(5)
    case _      => throw new Exception("Mode not supported")
  }
  val environment: Environment = Context.orThrow("ENVIRONMENT") match {
    case "local"      => Environment.Local()
    case "production" => Environment.Production()
  }
}

object Resources {
  def res[A <: Blockchain, C <: Sink, D <: Persistence](
      persist: Resource[IO, Persist[D]],
      forward: Resource[IO, Forward[C]]
  )(implicit rpc: Resource[IO, RPC[A]]) =
    for {
      f <- forward
      p <- persist
      r <- rpc
    } yield (r, f, p)

  def local[A <: Blockchain](
      )(implicit rpc: Resource[IO, RPC[A]]) = {
    val persist = memInstance
    val sink = loggingInstance
    res(persist, sink)
  }

  def production[A <: Blockchain]()(
      implicit rpc: Resource[IO, RPC[A]],
      cs: ContextShift[IO]
  ) = {
    val persist = postgresInstance
    val forward = kafkaInstance
    res(persist, forward)
  }
}
