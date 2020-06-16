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

import cats.effect.IO
import cats.effect.Timer
import cats.effect.Resource

import org.apache.avro.generic.GenericRecord
import scala.concurrent.duration.FiniteDuration

import com.madewithtea.blockchainimporter._
import com.madewithtea.blockchainimporter.strategies._

case class BlockData(
    blockNumber: Long,
    hash: String, 
    previousHash: Option[String],
    topicsAndRecords: Map[String, Seq[GenericRecord]]
)

sealed trait Environment
object Environment {
  case class Local() extends Environment
  case class Production() extends Environment
}

sealed trait Mode
object Mode {
  case class Push() extends Mode
  case class PushAndVerify(amountOfHashes: Int) extends Mode
  case class Lagging(lagBehind: Int, pollTime: FiniteDuration) extends Mode
}

trait Blockchain
object Blockchain {
  case class Bitcoin() extends Blockchain
  case class Omni() extends Blockchain
  case class Test() extends Blockchain
}

trait Sink
object Sink {
  case class Kafka() extends Sink
  case class LoggingOutput() extends Sink
  case class TestSink() extends Sink
}

trait Persistence
object Persistence {
  case class MemPersistence() extends Persistence
  case class Postgres() extends Persistence
}

trait RPC[A <: Blockchain] {
  def getDataByHeight(height: Long): IO[BlockData]
  def getDataByHash(hash: String): IO[BlockData]
  def getBestBlock(): IO[Long]
  def getNextBlock(): IO[String]
}

trait Forward[A <: Sink] {
  def forward(b: BlockData): IO[Unit]
}

trait Persist[A <: Persistence] {
  def getOffset(): IO[Long]
  def setOffset(offset: Long): IO[Unit]

  def getAllHeights(): IO[Seq[Long]]
  def getForwardedHash(height: Long): IO[Option[String]]
  def setForwardedHash(height: Long, hash: String): IO[Unit]
  def removeForwardedHash(height: Long): IO[Unit]
}

trait Backend[A <: Blockchain, B <: Sink, C <: Persistence] {
  val rpc: RPC[A]
  val forward: Forward[B]
  val persist: Persist[C]
}

trait HasBlockNumber {
  val blockNumber: Long
}
