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
package com.madewithtea.blockchainimporter.extractors

import cats.effect.Timer
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration._
import com.madewithtea.blockchainimporter._
import scala.concurrent.ExecutionContext
import com.madewithtea.blockchainimporter.persistence.MemPersistence

object TestDependencies {

  type TestBackend = Backend[
    Blockchain.Test,
    Sink.TestSink,
    Persistence.MemPersistence
  ]

  implicit val timer = IO.timer(ExecutionContext.global)

  def createChain(start: Long, end: Long): Seq[BlockData] =
    Range.Long(start, end, 1L).map { i =>
      BlockData(i, s"hash-of-$i", Some(s"hash-of-${i - 1}"), Map())
    }

  def createChainByHeight(start: Long, end: Long): Map[Long, BlockData] = 
    createChain(start, end).map { b => (b.blockNumber -> b) }.toMap

  def createChainByHash(start: Long, end: Long): Map[String, BlockData] = 
    createChain(start, end).map { b => (b.hash -> b) }.toMap

  def testBlock(number: Long, hash: String): BlockData =
    BlockData(number, hash, Some(s"parent of ${hash}"), Map())

  def rpc(throwException: Boolean = false) =
    new RPC[Blockchain.Test] with LazyLogging {

      var nextBlock: String = ""
      var bestBlock: Long = Long.MinValue
      var dataByHeight: Map[Long, BlockData] = Map()
      var dataByHash: Map[String, BlockData] = Map()

      def getDataByHeight(height: Long): IO[BlockData] =
        if (throwException)
          IO.raiseError(new Exception("Some error"))
        else
          for {
            _ <- IO(logger.debug(s"getDataByHeight $height"))
          } yield dataByHeight(height)

      def getDataByHash(hash: String): IO[BlockData] =
        if (throwException)
          IO.raiseError(new Exception("Some error"))
        else
          for {
            _ <- IO(logger.debug(s"getDataByHash ${hash}"))
          } yield dataByHash(hash)

      def getBestBlock(): IO[Long] =
        if (throwException)
          IO.raiseError(new Exception("Some error"))
        else
          for {
            _ <- IO(logger.debug(s"getBestBlock"))
          } yield bestBlock

      def getNextBlock(): IO[String] =
        if (throwException)
          IO.raiseError(new Exception("Some error"))
        else
          for {
            _ <- IO(logger.debug(s"getNextBlock"))
          } yield nextBlock
    }

  def persist(throwException: Boolean = false) =
    new Persist[Persistence.MemPersistence] with LazyLogging {
      val underlying = new MemPersistence(-1L)

      def offset: Long = underlying.getOffset().unsafeRunSync()
      def heights: Seq[Long] = underlying.getAllHeights().unsafeRunSync()
      def getAllHeights(): IO[Seq[Long]] = underlying.getAllHeights()
      def removeForwardedHash(height: Long): IO[Unit] =
        underlying.removeForwardedHash(height)
      def setForwardedHash(height: Long, hash: String): IO[Unit] =
        underlying.setForwardedHash(height, hash)
      def getForwardedHash(height: Long): IO[Option[String]] =
        underlying.getForwardedHash(height)
      def setOffset(offset: Long): IO[Unit] =
        if (throwException)
          IO.raiseError(new Exception("Some error"))
        else
          underlying.setOffset(offset)
      def getOffset(): IO[Long] =
        if (throwException)
          IO.raiseError(new Exception("Some error"))
        else
          underlying.getOffset()
    }

  def sink(throwException: Boolean = false) = new Forward[Sink.TestSink] {
    var forwarded: Seq[BlockData] = Seq()

    def forward(block: BlockData): IO[Unit] =
      if (throwException) {
        IO.raiseError(new Exception("Some error"))
      } else {
        IO {
          forwarded = forwarded ++ Seq[BlockData](block)
        }
      }
  }

  def backend(
      faultyRPC: Boolean = false,
      faultyPersistence: Boolean = false,
      faultySink: Boolean = false
  ) = new TestBackend {
    val rpc = TestDependencies.rpc(faultyRPC)
    val persist = TestDependencies.persist(faultyPersistence)
    val forward = TestDependencies.sink(faultySink)
  }
}

object ExtractorRunners extends LazyLogging {
  import TestDependencies._

  def runUntilExtractor(
      maxIterations: Long,
      backend: TestBackend,
      currentRun: Long = 0,
      state: StateData = StateData()
  ) = {
    val stepFunc = (s: StateData, b: TestBackend) => Push.step(s, b)
    runUntil(stepFunc, maxIterations, backend, currentRun, state)
  }

  def runUntilPushAndVerifyExtractor(
      maxIterations: Long,
      amountOfHashes: Int,
      backend: TestBackend,
      currentRun: Long = 0,
      state: StateData = StateData()
  ) = {
    val stepFunc = (s: StateData, b: TestBackend) =>
      for {
        state <- PushAndVerify.step(s, amountOfHashes, b)
        _ <- IO(logger.info(state.toString()))
      } yield state
    runUntil(stepFunc, maxIterations, backend, currentRun, state)
  }

  def runUntilLaggingExtractor(
      maxIterations: Long,
      lagBehind: Int,
      backend: TestBackend,
      currentRun: Long = 0,
      state: StateData = StateData()
  ) = {
    val stepFunc = (s: StateData, b: TestBackend) =>
      Lagging.step(s, lagBehind, 0.seconds, b)
    runUntil(stepFunc, maxIterations, backend, currentRun, state)
  }

  def runUntil(
      stepFunc: (StateData, TestBackend) => IO[StateData],
      maxIterations: Long,
      backend: TestBackend,
      currentRun: Long = 0,
      state: StateData = StateData()
  ): IO[StateData] = {
    for {
      nextState <- stepFunc(state, backend)
      _ <- IO(logger.debug(nextState.toString()))
      nextRun <- if (currentRun == maxIterations)
        IO(nextState)
      else runUntil(stepFunc, maxIterations, backend, currentRun + 1, nextState)
    } yield nextRun
  }
}
