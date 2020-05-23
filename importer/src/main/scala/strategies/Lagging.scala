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
package com.madewithtea.blockchainimporter.strategies

import cats.effect.IO
import cats.effect._

import com.madewithtea.blockchainimporter._
import scala.concurrent.duration.FiniteDuration

/**
  * The Lagging strategy of the extractor is extracting new blocks from 
  * the full node RPC in a busy waiting polling manner. The poll time is 
  * a parameter. Further, this strategy is lagging behind 'lagbehind' amount 
  * of blocks to avoid the propagation of wrong blocks due to reorganizations.
  */
object Lagging {

  def step[A <: Blockchain, B <: Sink, C <: Persistence](
      state: StateData,
      lagbehind: Int,
      pollTime: FiniteDuration,
      backend: Backend[A, B, C]
  )(
      implicit t: Timer[IO]
  ): IO[StateData] = state.currentState match {
    case CatchUp =>
      for {
        _ <- IO.sleep(pollTime)
        endState <- catchup(state, backend, lagbehind)
      } yield endState
    case _ => throw new Exception("State transition not allowed")
  }

  /**
    * In the catchup state, the extractor will catch up from the current block
    * number to the best known block number and sequentially updates the
    * persistence.
    */
  def catchup[A <: Blockchain, B <: Sink, C <: Persistence](
      state: StateData,
      backend: Backend[A, B, C],
      lagbehind: Int
  ): IO[StateData] =
    for {
      nextOpt <- IO(state.next)
      next <- nextOpt match {
        case Some(next) => IO.pure(next)
        case None       => backend.persist.getOffset()
      }
      best <- backend.rpc.getBestBlock()
      io <- if (next + lagbehind <= best) {
        forwardStep(next, best, backend)
      } else IO(StateData(Some(next), Some(best), CatchUp))
    } yield io

  /**
    * In the catchup state the forwardStep is called, if there is a block
    * which should be forwarded. It also updates the persistence and state.
    * The state stays CatchUp after forward.
    */
  def forwardStep[
      A <: Blockchain,
      B <: Sink,
      C <: Persistence
  ](
      next: Long,
      best: Long,
      backend: Backend[A, B, C]
  ): IO[StateData] =
    for {
      data <- backend.rpc.getDataByHeight(next)
      _ <- backend.forward.forward(data)
      _ <- backend.persist.setOffset(next + 1)
    } yield StateData(Some(next + 1), Some(best), CatchUp)
}
