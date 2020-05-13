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
package com.madewithtea.blockchainimporter.bitcoin

import cats.effect._
import cats.effect.IO
import cats.effect.Resource
import com.sksamuel.avro4s.RecordFormat
import com.madewithtea.blockchainrpc.bitcoin.Protocol.{
  BlockResponse,
  TransactionResponse,
  TransactionResponseVin,
  TransactionResponseVout
}

import com.madewithtea.blockchainrpc.Config
import com.madewithtea.blockchainrpc.{RPCClient, Config, ZeroMQ}
import com.madewithtea.protocol.bitcoin.importer._
import com.madewithtea.streams.common.HexTools._
import com.madewithtea.streams.common.Context
import org.http4s.client.Client

import scala.util.Try
import scala.concurrent.ExecutionContext

import com.madewithtea.streams.extractor.Blockchain
import com.madewithtea.streams.extractor.RPC
import com.madewithtea.streams.extractor.BlockData

object Instances {

  import Codec._
  import com.madewithtea.blockchainrpc.bitcoin.Syntax._

  implicit def bitcoinResource(
      implicit 
      ec: ExecutionContext,
      cs: ContextShift[IO]
  ): Resource[IO, RPC[Blockchain.Bitcoin]] = {
    val config = Config.fromEnv
    for {
      rpc <- RPCClient.bitcoin(
        config.hosts,
        config.port,
        config.username,
        config.password,
        config.zmqPort
      )
      interface <- Resource.make {
        IO(new RPC[Blockchain.Bitcoin] {
          private def retrieve(block: BlockResponse): IO[BlockData] =
            for {
              hashes <- IO.pure(block.tx)
              transactions <- rpc.getTransactions(hashes)
              blockData <- IO.pure(decode(block, transactions.seq.toList))
            } yield blockData

          def getDataByHeight(height: Long): IO[BlockData] =
            for {
              block <- rpc.getBlockByHeight(height)
              data <- retrieve(block)
            } yield data

          def getDataByHash(hash: String): IO[BlockData] =
            for {
              block <- rpc.getBlockByHash(hash)
              data <- retrieve(block)
            } yield data

          def getBestBlock(): IO[Long] =
            rpc.getBestBlockHeight()

          def getNextBlock(): IO[String] = rpc.getNextBlockHash()
        })
      } { _ =>
        IO.unit
      }
    } yield interface 
  }
}
