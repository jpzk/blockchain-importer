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
import com.madewithtea.blockchainimporter.persistence._
import com.typesafe.scalalogging.LazyLogging

/**
  * The PushAndVerify strategy is based on the
  * @see com.madewithtea.blockchainimporter.extractors.Push strategy and is further
  * recording block hashes to ensure the correctness of hash linkage between blocks.
  * If the constraint is violated a reorganization has occured and all wrongly propagated
  * blocks (in their height) are replayed from the winning fork.
  */
object PushAndVerify extends LazyLogging {

  def step[A <: Blockchain, B <: Sink, C <: Persistence](
      state: StateData,
      amountOfHashes: Int,
      backend: Backend[A, B, C]
  ): IO[StateData] =
    for {
      queue <- IO.pure(HashBlockQueue.limit(backend.persist, amountOfHashes))
      next <- backend.persist.getOffset()
      best <- backend.rpc.getBestBlock()
      _ <- queue.fillIfEmpty(next - 1, backend.rpc) // assure that queue is full
      block <- getBlock(next, state.currentState, backend.rpc)
      endState <- if (next == block.blockNumber)
        verifyAndForward(block, queue, best, backend)
      else
        for {
          recovered <- IO.pure(StateData(Some(next), Some(best), CatchUp))
          _ <- IO(
            logger.warn(
              s"Unexpected block received, fill gap with new state ${recovered}"
            )
          )
        } yield recovered
    } yield endState

  /**
    * If the order of the blocks is correct, and there are no missing blocks
    * then verify the hash link property and forward accordingly; if not valid
    * run LCA and set to catchup with blocks to be replayed.
    *
    * @param block
    * @param queue
    * @param best
    * @param backend
    * @return
    */
  def verifyAndForward[A <: Blockchain, B <: Sink, C <: Persistence](
      block: BlockData,
      queue: HashBlockLimitedQueue,
      best: Long,
      backend: Backend[A, B, C]
  ): IO[StateData] =
    for {
      valid <- queue.verify(block)
      state <- if (valid)
        forward(block, best, queue, backend)
      else
        for {
          recovered <- lca(queue, backend, best, block.blockNumber - 1)
          _ <- IO(logger.warn(s"Reorganization, new state: ${recovered}"))
        } yield recovered
    } yield {
      state
    }

  /**
    * Depending on the state will either get the block from
    * the RPC via poll based, or push based (blocking call)
    * via ZMQ or other stream endpoint.
    *
    * @param next
    * @param state
    * @param rpc
    * @return
    */
  def getBlock[A <: Blockchain](
      next: Long,
      state: State,
      rpc: RPC[A]
  ): IO[BlockData] = state match {
    case CatchUp => rpc.getDataByHeight(next)
    case Ongoing =>
      for {
        hash <- rpc.getNextBlock()
        block <- rpc.getDataByHash(hash)
      } yield block
  }

  /**
    * Forwarding the block in case the block hash linkage constraint
    * is valid. Forwards the block and sets the new offset. If the
    * offset is at the tip of the chain, it will switch to ongoing mode.
    *
    * @param block
    * @param best
    * @param backend
    * @return
    */
  def forward[A <: Blockchain, B <: Sink, C <: Persistence](
      block: BlockData,
      best: Long,
      queue: HashBlockLimitedQueue,
      backend: Backend[A, B, C]
  ): IO[StateData] =
    for {
      _ <- backend.forward.forward(block)
      _ <- backend.persist.setOffset(block.blockNumber + 1)
      _ <- queue.append(block.blockNumber, block.hash)
    } yield {
      if (block.blockNumber < best) {
        StateData(Some(block.blockNumber + 1), Some(best), CatchUp)
      } else {
        StateData(Some(block.blockNumber + 1), Some(block.blockNumber), Ongoing)
      }
    }

  /**
    * Least common ancestor recursive search with limit hash queue.
    * It will find the last correct propagated block height in case
    * of a reorg, and will return the state with the offset that
    * is needed to be replayed.
    *
    * @param queue
    * @param backend
    * @param best
    * @param height
    * @return
    */
  def lca[A <: Blockchain, B <: Sink, C <: Persistence](
      queue: HashBlockLimitedQueue,
      backend: Backend[A, B, C],
      best: Long,
      height: Long
  ): IO[StateData] =
    for {
      rpc <- backend.rpc.getDataByHeight(height)
      stored <- queue.get(height)
      state <- stored match {
        case Some(h) =>
          if (rpc.hash == h)
            IO.pure(StateData(Some(height + 1), Some(best), CatchUp))
          else
            lca(queue, backend, best, height - 1)
        case None =>
          IO.raiseError(new Exception(s"Hash for ${height} not persisted"))
      }
    } yield state
}
