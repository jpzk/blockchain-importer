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

import com.madewithtea.blockchainimporter.{BlockData}
import com.madewithtea.blockchainimporter._

/**
  * The Push strategy is a push-based extraction initiated from the 
  * full node RPC when a new block is available. The strategy is using 
  * both a CatchUp and an Ongoing mode. If last offset recorded is not the 
  * tip of the chain, this strategy will catchup to the tip of the chain 
  * and then will switch to the Ongoing mode, which will wait (blocking) 
  * for new blocks on the full node RPC. 
  */
object Push {

  def step[A <: Blockchain, B <: Sink, C <: Persistence](
      state: StateData,
      backend: Backend[A, B, C]
  ): IO[StateData] = state.currentState match {
    case CatchUp => catchup(state, backend)
    case Ongoing =>
      state match {
        case StateData(Some(next), Some(best), Ongoing) =>
          ongoing(StrictStateData(next, best, Ongoing), backend)
        case _ =>
          IO.raiseError(new Exception("Ongoing missing next and/or best"))
      }
  }

  /**
    * In the catchup state, the extractor will catch up from the current block
    * number to the best known block number and sequentially updates the
    * persistence.
    */
  def catchup[
      A <: Blockchain,
      B <: Sink,
      C <: Persistence
  ](state: StateData, backend: Backend[A, B, C]): IO[StateData] =
    for {
      nextOpt <- IO(state.next)
      next <- nextOpt match {
        case Some(next) => IO.pure(next)
        case None       => backend.persist.getOffset()
      }
      best <- backend.rpc.getBestBlock()
      io <- if (next <= best) {
        forwardStep(next, best, backend)
      } else IO(StateData(Some(next), Some(best), Ongoing))
    } yield io

  /**
    * In the catchup state the forwardStep is called, if there is a block
    * which should be forwarded. It also updates the persistence and state.
    * The state stays CatchUp after forward.
    */
  def forwardStep[A <: Blockchain, B <: Sink, C <: Persistence](
      next: Long,
      best: Long,
      backend: Backend[A, B, C]
  ): IO[StateData] =
    for {
      data <- backend.rpc.getDataByHeight(next)
      _ <- backend.forward.forward(data)
      _ <- backend.persist.setOffset(next + 1)
    } yield StateData(Some(next + 1), Some(best), CatchUp)

  /**
    * In the ongoing state, the extractor consumes from the ZMQ subscription.
    * It gets the next block (blocking call) and writes the next offset to get
    * to persistence, further it updates the best block number to be the last
    * fetched block.
    */
  def ongoing[
      A <: Blockchain,
      B <: Sink,
      C <: Persistence
  ](state: StrictStateData, backend: Backend[A, B, C])(): IO[StateData] =
    for {
      newBlock <- backend.rpc.getNextBlock()
      data <- backend.rpc.getDataByHash(newBlock)
      op <- if (data.blockNumber < state.next)
        reorg(data, state, backend)
      else if (data.blockNumber > state.best + 1)
        missingBlock(data, state)
      else
        expected(data, backend)
    } yield op

  /**
    * In the ongoing state, when the extractor receives a block which is below the
    * expected next block a reorg happened, in this scenario, we will not update the
    * next and best block, but still forward the data.
    */
  def reorg[
      A <: Blockchain,
      B <: Sink,
      C <: Persistence,
  ](
      data: BlockData,
      state: StrictStateData,
      backend: Backend[A, B, C]
  ): IO[StateData] =
    for {
      _ <- backend.forward.forward(data)
      _ <- backend.persist.setOffset(state.next)
    } yield StateData(Some(state.next), Some(state.best), Ongoing)

  /**
    * In the ongoing state, when the extractor receives a block which is the expected
    * next block height, forward and update the next block to Block + 1 and best
    * block to be the last seen block.
    */
  def expected[
      A <: Blockchain,
      B <: Sink,
      C <: Persistence,
  ](data: BlockData, backend: Backend[A, B, C])(): IO[StateData] =
    for {
      _ <- backend.forward.forward(data)
      _ <- backend.persist.setOffset(data.blockNumber + 1)
    } yield StateData(Some(data.blockNumber + 1), Some(data.blockNumber), Ongoing)

  /**
    * In the ongoing state, when the extractor receives a block but it's above the
    * expected next block number, then there's a gap which needs to be filled. Therefore,
    * we update the best block to the last seen block, and keep the next block as is.
    * Further, we change the state back to CatchUp, so it will replay the missing blocks
    */
  def missingBlock[A](data: BlockData, state: StrictStateData): IO[StateData] =
    IO(StateData(Some(state.next), Some(data.blockNumber), CatchUp))
}
