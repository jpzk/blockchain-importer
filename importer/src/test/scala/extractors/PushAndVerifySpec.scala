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

import org.scalatest.{FlatSpec, Matchers}
import com.madewithtea.blockchainimporter.persistence._
import com.madewithtea.blockchainimporter.BlockData

class PushAndVerifySpec extends FlatSpec with Matchers {
  import TestDependencies._

  behavior of "push and verify extractor"

  it should "throw exception when called with state Ongoing but no state" in {
    val b = backend()
    assertThrows[Exception] {
      PushAndVerify.step(StateData(None, None, Ongoing), 5, b).unsafeRunSync()
    }
  }

  it should "propagate RPC exceptions from backend" in {
    val b = backend(faultyRPC = true)
    assertThrows[Exception] {
      PushAndVerify.step(StateData(), 5, b).unsafeRunSync()
    }
  }

  it should "propagate persistence exceptions from backend" in {
    val b = backend(faultyPersistence = true)
    assertThrows[Exception] {
      PushAndVerify.step(StateData(), 5, b).unsafeRunSync()
    }
  }

  it should "propagate sink exceptions from backend" in {
    val b = backend(faultySink = true)
    b.rpc.bestBlock = 10
    b.persist.setOffset(1)
    assertThrows[Exception] {
      PushAndVerify.step(StateData(), 5, b).unsafeRunSync()
    }
  }

  it should "switch to ongoing and forward data" in {
    val b = backend()
    b.persist.setOffset(6).unsafeRunSync()
    b.rpc.nextBlock = "hash-of-17"
    b.rpc.bestBlock = 16
    b.rpc.dataByHeight = createChainByHeight(0, 20)
    b.rpc.dataByHash = createChainByHash(0, 20)

    val endState =
      ExtractorRunners.runUntilPushAndVerifyExtractor(11, 3, b).unsafeRunSync()
    b.persist.heights.sorted shouldEqual Seq(15, 16, 17)
    endState.best shouldEqual Some(17)
    endState.next shouldEqual Some(18)
  }

  it should "replay blocks if there's a gap" in {
    val b = backend()

    b.persist.setOffset(9).unsafeRunSync()
    b.rpc.bestBlock = 9
    b.rpc.nextBlock = "hash-of-9"

    val blockZ = BlockData(8, "hash-of-8", Some("hash-of-7"), Map())
    val blockA = BlockData(9, "hash-of-9", Some("hash-of-8"), Map())
    val blockB = BlockData(100, "hash-of-100", Some("hash-of-99"), Map())

    b.rpc.dataByHeight = Map(8L -> blockZ, 9L -> blockA, 100L -> blockB)
    b.rpc.dataByHash = Map(
      "hash-of-8" -> blockZ,
      "hash-of-9" -> blockA,
      "hash-of-100" -> blockB
    )

    val initState = StateData(Some(9), Some(8), Ongoing)
    val nextState = PushAndVerify.step(initState, 1, b).unsafeRunSync()

    b.rpc.bestBlock = 100
    b.rpc.nextBlock = "hash-of-100"

    val endState = PushAndVerify.step(nextState, 1, b).unsafeRunSync()
    endState.currentState shouldEqual CatchUp
    endState.best shouldEqual Some(100)
    endState.next shouldEqual Some(10)
  }

  it should "replay blocks if there's a reorg" in {
    val b = backend()

    b.persist.setOffset(11).unsafeRunSync()
    b.rpc.bestBlock = 10
    b.rpc.nextBlock = "hash-of-11"

    val blockY = BlockData(9, "hash-of-9", Some("hash-of-8"), Map())
    val blockZ = BlockData(10, "hash-of-10", Some("hash-of-9"), Map())
    val blockA = BlockData(11, "hash-of-11", Some("hash-of-10"), Map())
    val blockB = BlockData(12, "hash-of-12", Some("hash-of-11-fork"), Map())

    b.rpc.dataByHeight =
      Map(9L -> blockY, 10L -> blockZ, 11L -> blockA, 12L -> blockB)

    b.rpc.dataByHash = Map(
      "hash-of-11" -> blockA,
      "hash-of-12" -> blockB,
      "hash-of-10" -> blockZ,
      "hash-of-9" -> blockY
    )

    val initState = StateData(Some(11), Some(10), Ongoing)
    val nextState = PushAndVerify.step(initState, 2, b).unsafeRunSync()

    b.rpc.nextBlock = "hash-of-12"
    b.rpc.bestBlock = 12

    nextState.currentState shouldEqual Ongoing
    nextState.best shouldEqual Some(11)
    nextState.next shouldEqual Some(12)
    b.persist.offset shouldEqual 12

    val middleState = PushAndVerify.step(nextState, 2, b).unsafeRunSync()

    b.rpc.nextBlock = "hash-of-13"
    b.rpc.bestBlock = 13

    middleState.currentState shouldEqual CatchUp
    middleState.best shouldEqual Some(12)
    middleState.next shouldEqual Some(12)
    b.persist.offset shouldEqual 12

    // reorg forces catchup from block 12.

    val blockBcorrect = BlockData(12, "hash-of-12", Some("hash-of-11"), Map())

    b.rpc.dataByHeight =
      Map(9L -> blockY, 10L -> blockZ, 11L -> blockA, 12L -> blockBcorrect)

    b.rpc.dataByHash = Map(
      "hash-of-11" -> blockA,
      "hash-of-12" -> blockBcorrect,
      "hash-of-10" -> blockZ,
      "hash-of-9" -> blockY
    )

    val endState = PushAndVerify.step(middleState, 2, b).unsafeRunSync()

    endState.currentState shouldEqual CatchUp
    endState.best shouldEqual Some(13)
    endState.next shouldEqual Some(13)
    b.persist.offset shouldEqual 13
  }

  it should "in state ongoing should forward data and update persistence and state" in {
    val b = backend()

    b.persist.setOffset(11).unsafeRunSync()
    b.rpc.bestBlock = 10
    b.rpc.nextBlock = "hash-of-11"

    b.rpc.dataByHeight = createChainByHeight(5, 14)
    b.rpc.dataByHash = createChainByHash(5, 14)

    val initState = StateData(Some(11), Some(10), Ongoing)

    val nextState = PushAndVerify.step(initState, 2, b).unsafeRunSync()
    nextState.best shouldEqual Some(11)
    nextState.next shouldEqual Some(12)
    b.persist.offset shouldEqual 12

    b.forward.forwarded shouldEqual List(
      BlockData(11, "hash-of-11", Some("hash-of-10"), Map())
    )
    b.persist.heights.sorted shouldEqual Seq(10, 11)
    b.rpc.nextBlock = "hash-of-12"

    val endState = PushAndVerify.step(nextState, 2, b).unsafeRunSync()
    endState.best shouldEqual Some(12)
    endState.next shouldEqual Some(13)

    b.persist.heights.sorted shouldEqual Seq(11, 12)
    b.forward.forwarded shouldEqual
      Seq(
        BlockData(11, "hash-of-11", Some("hash-of-10"), Map()),
        BlockData(12, "hash-of-12", Some("hash-of-11"), Map())
      )
  }
}
