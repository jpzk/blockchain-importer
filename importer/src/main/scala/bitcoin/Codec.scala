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

import cats.effect.IO
import com.sksamuel.avro4s.RecordFormat

import com.madewithtea.blockchainrpc.bitcoin.Protocol.{
  BlockResponse,
  TransactionResponse,
  TransactionResponseVin,
  TransactionResponseVout
}

import com.madewithtea.blockchainrpc.{RPCClient, Config, ZeroMQ}
import com.madewithtea.protocol.bitcoin.extractor._
import com.madewithtea.streams.common.HexTools._
import org.http4s.client.Client

import scala.util.Try

import com.madewithtea.streams.extractor.{RPC, BlockData}

object Codec {
  val blockFmt = RecordFormat[Block]
  val txFmt = RecordFormat[Transaction]
  val scriptFmt = RecordFormat[Script]
  
  val blockTopic = "blocks"
  val transactionTopic = "transactions"
  val scriptTopic = "scripts"

  def decode(
      block: BlockResponse,
      txs: List[TransactionResponse]
  ): BlockData = BlockData(
      block.height,
      block.hash,
      block.previousblockhash,
      Map(
        blockTopic -> Seq(blockFmt.to(decodeBlock(block, txs))),
        transactionTopic -> (decodeCoinbaseTransaction(block, txs.head) +:
          decodeTransactions(block, txs.tail)).map(txFmt.to),
        scriptTopic -> decodeScripts(txs, block.height).map(scriptFmt.to)
      )
    )

  def decodeBlock(
      block: BlockResponse,
      txs: List[TransactionResponse]
  ): Block = Block(
      blockNumber = block.height,
      blockHash = hex2bytes(block.hash),
      parentHash = Try(block.previousblockhash.get)
        .map(hex2bytes)
        .getOrElse(Array[Byte]()),
      nonce = block.nonce,
      strippedSize = block.strippedsize,
      merkleRoot = hex2bytes(block.merkleroot),
      version = block.version,
      weight = block.weight,
      difficulty = block.difficulty,
      chainWork = hex2bytes(block.chainwork),
      bits = hex2bytes(block.bits),
      size = block.size,
      medianTime = block.mediantime,
      timestamp = block.time,
      transactionsCount = block.nTx,
      coinbase = Coinbase(
        transactionId = hex2bytes(txs.head.txid),
        coinbase = txs.head.vin.head.coinbase match {
          case Some(cb) => hex2bytes(cb)
          case None =>
            throw new Exception("No coinbase script on coinbase transaction")
        },
        sequence = txs.head.vin.head.sequence
      ),
      transactions = block.tx.map(hex2bytes)
    )

  def decodeCoinbaseTransaction(
      block: BlockResponse,
      tx: TransactionResponse
  ): Transaction = Transaction(
      transactionId = hex2bytes(tx.txid),
      hash = hex2bytes(tx.hash),
      blockNumber = block.height,
      blockHash = hex2bytes(block.hash),
      index = 0,
      version = tx.version,
      size = tx.size,
      vsize = tx.vsize,
      weight = tx.weight,
      locktime = tx.locktime,
      timestamp = tx.time,
      inputs = Seq(),
      outputs = tx.vout.map(decodeOutput)
    )

  def decodeTransactions(
      block: BlockResponse,
      txs: List[TransactionResponse]
  ): Seq[Transaction] = txs.zipWithIndex.map {
      case (tx, index) =>
        Transaction(
          transactionId = hex2bytes(tx.txid),
          hash = hex2bytes(tx.txid),
          blockNumber = block.height,
          blockHash = hex2bytes(block.hash),
          index = index + 1,
          version = tx.version,
          size = tx.size,
          vsize = tx.vsize,
          weight = tx.weight,
          locktime = tx.locktime,
          timestamp = tx.time,
          inputs = tx.vin.map(this.decodeInput),
          outputs = tx.vout.map(this.decodeOutput)
        )
    }

  def decodeOutput(vout: TransactionResponseVout) = Output(
      index = vout.n,
      bitcoinValue = vout.value,
      scriptPubKeyReqSigs = vout.scriptPubKey.reqSigs match {
        case Some(n) => n
        case None    => 0
      },
      scriptPubKeyType = vout.scriptPubKey.`type`,
      scriptPubKeyAddresses = vout.scriptPubKey.addresses match {
        case Some(list) => list.toArray
        case None       => Array()
      }
    )

  def decodeInput(vin: TransactionResponseVin) = Input(
      previousTransactionId = vin.txid match {
        case Some(id) => hex2bytes(id)
        case None     => throw new Exception("txid on vin missing")
      },
      vout = vin.vout.get,
      scriptSig = hex2bytes(vin.scriptSig.get.hex)
    )
  
  def decodeScripts(txs: List[TransactionResponse], height: Long) =
    txs.zipWithIndex
      .flatMap { case (tx, txIndex) => getScripts(txIndex, tx, height) }

  def getScripts(
      txIndex: Int,
      tx: TransactionResponse,
      height: Long
  ): Seq[Script] = {
    tx.vout.zipWithIndex
      .map {
        case (output, index) =>
          Script(height, txIndex, index, hex2bytes(output.scriptPubKey.hex))
      }
  }
}
