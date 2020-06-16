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

sealed trait State
case object CatchUp extends State
case object Ongoing extends State

case class StateData(
    next: Option[Long] = None, // next block to consume
    best: Option[Long] = None, // the best block available from RPC
    currentState: State = CatchUp 
)

case class StrictStateData(
    next: Long,
    best: Long,
    currentState: State = Ongoing
)

trait ExtractorStep[A <: Blockchain, B <: Mode, C <: Sink, E <: Persistence] {
  def step(mode: B, state: StateData, backend: Backend[A, C, E])(
      implicit t: Timer[IO]
  ): IO[StateData]
}

object Instances {
  implicit def lagging[A <: Blockchain, B <: Sink, E <: Persistence](
      implicit t: Timer[IO]
  ) =
    new ExtractorStep[A, Mode.Lagging, B, E] {
      def step(mode: Mode.Lagging, state: StateData, backend: Backend[A, B, E])(
          implicit t: Timer[IO]
      ): IO[StateData] = {
        Lagging.step(
          state,
          mode.lagBehind,
          mode.pollTime,
          backend: Backend[A, B, E]
        )
      }
    }

  implicit def push[A <: Blockchain, B <: Sink, E <: Persistence, C](
      implicit t: Timer[IO]
  ) =
    new ExtractorStep[A, Mode.Push, B, E] {
      def step(mode: Mode.Push, state: StateData, backend: Backend[A, B, E])(
          implicit t: Timer[IO]
      ): IO[StateData] = {
        Push.step(state, backend)
      }
    }

  implicit def pushAndVerify[A <: Blockchain, B <: Sink, E <: Persistence, C](
      implicit t: Timer[IO]
  ) =
    new ExtractorStep[A, Mode.PushAndVerify, B, E] {
      def step(mode: Mode.PushAndVerify, state: StateData, backend: Backend[A, B, E])(
          implicit t: Timer[IO]
      ): IO[StateData] = {
        PushAndVerify.step(state, mode.amountOfHashes, backend)
      }
    }
}
