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

import com.madewithtea.blockchainimporter._

import com.typesafe.scalalogging.LazyLogging
import cats.effect.IO
import cats.effect._

object Utils extends LazyLogging {

  def loop[
      A <: Blockchain,
      B <: Mode,
      C <: Sink,
      E <: Persistence
  ](
      blockchain: A,
      mode: B,
      resources: Resource[IO, (RPC[A], Forward[C], Persist[E])],
      state: StateData = StateData()
  )(
      implicit extractor: ExtractorStep[A, B, C, E],
      timer: Timer[IO]
  ) = resources.use {
      case (_rpc, _forward, _persist) =>
        for {
          backend <- IO(new Backend[A, C, E] {
            val forward: Forward[C] = _forward
            val rpc: RPC[A] = _rpc
            val persist = _persist
          })
          s <- step(mode, state, backend)
        } yield ()
    }

  def step[
      A <: Blockchain,
      B <: Mode,
      C <: Sink,
      E <: Persistence
  ](
      mode: B,
      state: StateData = StateData(),
      backend: Backend[A, C, E]
  )(
      implicit extractor: ExtractorStep[A, B, C, E],
      timer: Timer[IO]
  ): IO[Unit] = for {
      newState <- extractor.step(mode, state, backend)
      _ <- IO(logger.info(newState.toString))
      l <- step(mode, newState, backend)
    } yield l
}
