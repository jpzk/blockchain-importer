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

import com.madewithtea.blockchainimporter.HasBlockNumber
import com.sksamuel.avro4s.AvroDoc

@AvroDoc("Block")
case class Block(
    blockNumber: Long,
    blockHash: Array[Byte],
    parentHash: Array[Byte],
    nonce: Long,
    strippedSize: Long,
    merkleRoot: Array[Byte],
    version: Int,
    weight: Int,
    difficulty: Double,
    chainWork: Array[Byte],
    bits: Array[Byte],
    size: Long,
    medianTime: Long,
    timestamp: Long,
    transactionsCount: Int,
    coinbase: Coinbase,
    transactions: Seq[Array[Byte]]
) extends HasBlockNumber

@AvroDoc("Coinbase input of a block")
case class Coinbase(
    transactionId: Array[Byte],
    coinbase: Array[Byte],
    sequence: Long
)

@AvroDoc("One of multiple transactions in a block")
case class Transaction(
    transactionId: Array[Byte],
    hash: Array[Byte],
    blockNumber: Long,
    blockHash: Array[Byte],
    index: Int, // added for watermarks exactly-once
    version: Int,
    size: Int,
    vsize: Int,
    weight: Int,
    locktime: Long,
    timestamp: Long,
    inputs: Seq[Input],
    outputs: Seq[Output]
)

@AvroDoc(
  "One of multiple pending / unconfirmed transactions from the memory pool"
)
// https://bitcoin.org/en/developer-reference#txin
@AvroDoc(
  "One of multiple inputs of a transaction, " +
    "https://bitcoin.org/en/developer-reference#txin"
)
case class Input(
    previousTransactionId: Array[Byte],
    vout: Int,
    scriptSig: Array[Byte]
)

@AvroDoc(
  "One of multiple outputs of a transaction, " +
    "https://bitcoin.org/en/developer-reference#txout"
)
case class Output(
    index: Int,
    bitcoinValue: Double,
    scriptPubKeyReqSigs: Int,
    scriptPubKeyType: String,
    scriptPubKeyAddresses: Array[String]
)

@AvroDoc("1:1 to output, pubkey script for an UTXO")
case class Script(
    blockNumber: Long,
    transactionIndex: Int,
    outputIndex: Int,
    scriptPubKey: Array[Byte]
)
