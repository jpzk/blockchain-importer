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

import java.nio.ByteBuffer

import com.sksamuel.avro4s.RecordFormat
import com.madewithtea.blockchainimporter.BlockData
import com.madewithtea.blockchainimporter.HexTools._
import com.madewithtea.blockchainrpc.bitcoin.Protocol
import com.madewithtea.blockchainrpc.bitcoin.Protocol._
import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.convert.Wrappers.SeqWrapper

class CodecSpec extends FlatSpec with Matchers {

  behavior of "Bitcoin Codec"

  val blockTopic = "blocks"
  val transactionTopic = "transactions"
  val scriptTopic = "scripts"

  val byteArrayFmt = RecordFormat[Array[Byte]]

  it should "decode RPC response classes into protocol classes correctly" in {
    val block = getBlockResponse
    val txs = getTransactionsResponse
    val blockData = Codec.decode(block, txs)
    val (blockRecords, transactionRecords) = assertHeaderProperties(blockData)
    assertBlockProps(blockRecords)
    assertCoinbaseProps(transactionRecords)
    assertTransactionProps(transactionRecords)
  }

  private def assertTransactionProps(transactionRecords: Seq[GenericRecord]) = {
    val tx1 = transactionRecords(1)
    tx1.get("transactionId") shouldEqual ByteBuffer.wrap(hex2bytes("456def"))
    tx1.get("hash") shouldEqual ByteBuffer.wrap(hex2bytes("456def"))
    tx1.get("blockNumber") shouldEqual 10
    tx1.get("blockHash") shouldEqual ByteBuffer.wrap(hex2bytes("012345"))
    tx1.get("index") shouldEqual 1
    tx1.get("version") shouldEqual 2
    tx1.get("size") shouldEqual 1234
    tx1.get("vsize") shouldEqual 1234
    tx1.get("locktime") shouldEqual 0L
    tx1.get("timestamp") shouldEqual 1234L

    val inputsTx1 = tx1.get("inputs").asInstanceOf[SeqWrapper[GenericRecord]]
    inputsTx1.size() shouldEqual 2
    inputsTx1.get(0).get("previousTransactionId") shouldEqual ByteBuffer.wrap(
      hex2bytes("456def")
    )
    inputsTx1.get(0).get("vout") shouldEqual 2
    inputsTx1.get(0).get("scriptSig") shouldEqual ByteBuffer.wrap(
      hex2bytes("456def")
    )

    inputsTx1.get(1).get("previousTransactionId") shouldEqual ByteBuffer.wrap(
      hex2bytes("456def")
    )
    inputsTx1.get(1).get("vout") shouldEqual 2
    inputsTx1.get(1).get("scriptSig") shouldEqual ByteBuffer.wrap(
      hex2bytes("456def")
    )

    val outputsTx1 = tx1.get("outputs").asInstanceOf[SeqWrapper[GenericRecord]]
    outputsTx1.size() shouldEqual 2

    outputsTx1.get(0).get("index") shouldEqual 0
    outputsTx1.get(0).get("bitcoinValue") shouldEqual 10
    outputsTx1.get(0).get("scriptPubKeyReqSigs") shouldEqual 3
    outputsTx1.get(0).get("scriptPubKeyType") shouldEqual new Utf8("456def")
    outputsTx1.get(0).get("scriptPubKeyAddresses") shouldEqual SeqWrapper(
      Seq(
        new Utf8("tx1-o0-address-0"),
        new Utf8("tx1-o0-address-1"),
        new Utf8("tx1-o0-address-2")
      )
    )

    outputsTx1.get(1).get("index") shouldEqual 1
    outputsTx1.get(1).get("bitcoinValue") shouldEqual 20
    outputsTx1.get(1).get("scriptPubKeyReqSigs") shouldEqual 2
    outputsTx1.get(1).get("scriptPubKeyType") shouldEqual new Utf8(
      "tx1-o1-type"
    )
    outputsTx1.get(1).get("scriptPubKeyAddresses") shouldEqual SeqWrapper(
      Seq(new Utf8("tx1-o1-address-0"), new Utf8("tx1-o1-address-1"))
    )
  }

  private def assertCoinbaseProps(transactionRecords: Seq[GenericRecord]) = {

    val cb = transactionRecords.head
    cb.get("transactionId") shouldEqual ByteBuffer.wrap(hex2bytes("abcdef"))
    cb.get("hash") shouldEqual ByteBuffer.wrap(hex2bytes("abcdef"))
    cb.get("blockNumber") shouldEqual 10
    cb.get("blockHash") shouldEqual ByteBuffer.wrap(hex2bytes("012345"))
    cb.get("index") shouldEqual 0
    cb.get("version") shouldEqual 2
    cb.get("size") shouldEqual 123
    cb.get("vsize") shouldEqual 123
    cb.get("weight") shouldEqual 1123
    cb.get("locktime") shouldEqual 0L
    cb.get("timestamp") shouldEqual 1234L
    cb.get("inputs") shouldEqual SeqWrapper(Seq())

    val cbOutputs = cb.get("outputs").asInstanceOf[SeqWrapper[GenericRecord]]
    cbOutputs.size() shouldEqual 1

    cbOutputs.get(0).get("index") shouldEqual 0
    cbOutputs.get(0).get("bitcoinValue") shouldEqual 12.5
    cbOutputs.get(0).get("scriptPubKeyReqSigs") shouldEqual 1
    cbOutputs.get(0).get("scriptPubKeyType") shouldEqual new Utf8("123abc")
    cbOutputs.get(0).get("scriptPubKeyAddresses") shouldEqual SeqWrapper(
      Seq(new Utf8("coinbase-o0-address"))
    )
    transactionRecords
  }

  private def assertBlockProps(blockRecords: Seq[GenericRecord]) = {
    val blockRecord = blockRecords.head
    blockRecord.get("blockNumber") shouldEqual 10
    blockRecord.get("blockHash") shouldEqual ByteBuffer.wrap(
      hex2bytes("012345")
    )
    blockRecord.get("parentHash") shouldEqual ByteBuffer.wrap(
      hex2bytes("567890")
    )
    blockRecord.get("nonce") shouldEqual 1234L
    blockRecord.get("strippedSize") shouldEqual 5678L
    blockRecord.get("merkleRoot") shouldEqual ByteBuffer.wrap(hex2bytes("ffff"))
    blockRecord.get("version") shouldEqual 2
    blockRecord.get("weight") shouldEqual 123
    blockRecord.get("difficulty") shouldEqual 9012L
    blockRecord.get("chainWork") shouldEqual ByteBuffer.wrap(hex2bytes("cccc"))
    blockRecord.get("bits") shouldEqual ByteBuffer.wrap(hex2bytes("aaaa"))
    blockRecord.get("size") shouldEqual 3456L
    blockRecord.get("medianTime") shouldEqual 7890L
    blockRecord.get("timestamp") shouldEqual 1234L
    blockRecord.get("transactionsCount") shouldEqual 2

    val coinbase = blockRecord.get("coinbase").asInstanceOf[GenericRecord]
    coinbase.get("transactionId") shouldEqual ByteBuffer.wrap(
      hex2bytes("abcdef")
    )
    coinbase.get("coinbase") shouldEqual ByteBuffer.wrap(hex2bytes("abcdef"))
    coinbase.get("sequence") shouldEqual 123

    blockRecord.get("transactions") shouldEqual SeqWrapper(
      Seq(
        ByteBuffer.wrap(hex2bytes("abcdef")),
        ByteBuffer.wrap(hex2bytes("f01234"))
      )
    )

  }

  private def assertHeaderProperties(blockData: BlockData) = {
    blockData.blockNumber shouldEqual 10
    blockData.topicsAndRecords.contains(blockTopic) shouldEqual true

    val blockRecords = blockData.topicsAndRecords(blockTopic)

    blockRecords.size shouldEqual 1

    val transactionRecords = blockData.topicsAndRecords(transactionTopic)

    transactionRecords.size shouldEqual 2

    (blockRecords, transactionRecords)
  }

  private def getTransactionsResponse = {
    val txs: List[TransactionResponse] = List(
      TransactionResponse(
        confirmations = Some(2),
        blockhash = "012345",
        blocktime = 1234L,
        hash = "abcdef",
        hex = "567890",
        txid = "abcdef",
        time = 1234L,
        vsize = 123,
        size = 123,
        weight = 1123,
        version = 2,
        vin = List(
          TransactionResponseVin(
            txid = None,
            coinbase = Some("abcdef"),
            sequence = 123,
            vout = None,
            scriptSig = None
          )
        ),
        vout = List(
          Protocol.TransactionResponseVout(
            value = 12.5,
            n = 0,
            scriptPubKey = TransactionResponseScript(
              asm = "123abc",
              hex = "123abc",
              reqSigs = Some(1),
              `type` = "123abc",
              addresses = Some(List("coinbase-o0-address"))
            )
          )
        ),
        locktime = 0L
      ),
      TransactionResponse(
        confirmations = Some(2),
        blockhash = "012345",
        blocktime = 1234L,
        hash = "456def",
        hex = "456def",
        txid = "456def",
        time = 1234L,
        vsize = 1234,
        size = 1234,
        weight = 1123,
        version = 2,
        vin = List(
          TransactionResponseVin(
            txid = Some("456def"),
            coinbase = None,
            sequence = 123,
            vout = Some(2),
            scriptSig =
              Some(TransactionResponseScriptSig(asm = "456def", hex = "456def"))
          ),
          TransactionResponseVin(
            txid = Some("456def"),
            coinbase = None,
            sequence = 123,
            vout = Some(2),
            scriptSig =
              Some(TransactionResponseScriptSig(asm = "456def", hex = "456def"))
          )
        ),
        vout = List(
          Protocol.TransactionResponseVout(
            value = 10,
            n = 0,
            scriptPubKey = TransactionResponseScript(
              asm = "456def",
              hex = "456def",
              reqSigs = Some(3),
              `type` = "456def",
              addresses = Some(
                List("tx1-o0-address-0", "tx1-o0-address-1", "tx1-o0-address-2")
              )
            )
          ),
          TransactionResponseVout(
            value = 20,
            n = 1,
            scriptPubKey = TransactionResponseScript(
              asm = "456def",
              hex = "456def",
              reqSigs = Some(2),
              `type` = "tx1-o1-type",
              addresses = Some(List("tx1-o1-address-0", "tx1-o1-address-1"))
            )
          )
        ),
        locktime = 0L
      )
    )
    txs
  }

  private def getBlockResponse = {
    val block = BlockResponse(
      height = 10,
      hash = "012345",
      previousblockhash = Some("567890"),
      nonce = 1234L,
      strippedsize = 5678L,
      merkleroot = "ffff",
      version = 2,
      weight = 123,
      difficulty = 9012L,
      chainwork = "cccc",
      bits = "aaaa",
      size = 3456L,
      mediantime = 7890L,
      time = 1234L,
      nTx = 2,
      tx = List("abcdef", "f01234")
    )
    block
  }
}
