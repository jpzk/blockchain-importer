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

class PushSpec extends FlatSpec with Matchers {
  import TestDependencies._

  behavior of "push extractor"

  it should "throw exception when called with state Ongoing but no state" in {
    val b = backend() 
    assertThrows[Exception] {
      Push.step(StateData(None, None, Ongoing), b).unsafeRunSync()
    }
  }

  it should "propagate RPC exceptions from backend" in {
    val b = backend(faultyRPC = true)
    assertThrows[Exception] {
      Push.step(StateData(), b).unsafeRunSync()
    }
  }

  it should "propagate persistence exceptions from backend" in {
    val b = backend(faultyPersistence = true)
    assertThrows[Exception] {
      Push.step(StateData(), b).unsafeRunSync()
    }
  }

  it should "propagate sink exceptions from backend" in {
    val b = backend(faultySink = true) 
    b.rpc.bestBlock = 10
    b.persist.setOffset(1)
    assertThrows[Exception] {
      Push.step(StateData(), b).unsafeRunSync()
    }
  }

  it should "switch to ongoing and forward data" in {
    val b = backend()
    b.persist.setOffset(0).unsafeRunSync()
    b.rpc.bestBlock = 10
    b.rpc.dataByHeight = Range
      .Long(0, 11, 1)
      .map { n =>
        (n -> testBlock(n, n.toString))
      }
      .toMap

    val endState = ExtractorRunners.runUntilExtractor(11, b).unsafeRunSync()
    endState.best shouldEqual Some(10)
    endState.next shouldEqual Some(11)
    endState.currentState shouldEqual Ongoing
  }

  it should "in state ongoing should forward data when block " +
    "number is smaller than best known (Reorg)" in {
    val b = backend()

    b.rpc.bestBlock = 10
    b.rpc.dataByHash =
      Map("a2" -> testBlock(11, "a2"), "b" -> testBlock(12, "b"))

    val initState = StateData(Some(12), Some(12), Ongoing)
    b.rpc.nextBlock = "b"

    val nextState = Push.step(initState, b).unsafeRunSync()
    nextState.best shouldEqual Some(12)
    nextState.next shouldEqual Some(13)
    b.forward.forwarded shouldEqual
      Seq(testBlock(12, "b"))

    b.rpc.nextBlock = "a2"

    val endState = Push.step(nextState, b).unsafeRunSync()
    endState.best shouldEqual Some(12)
    endState.next shouldEqual Some(13)
    b.forward.forwarded shouldEqual
      Seq(testBlock(12, "b"), testBlock(11, "a2"))
  }

  it should "in state ongoing should forward data and update persistence and state" in {
    val b = backend()

    b.rpc.bestBlock = 10
    b.rpc.nextBlock = "a"
    b.rpc.dataByHash =
      Map("a" -> testBlock(11, "a"), "b" -> testBlock(12, "b"))

    val initState = StateData(Some(11), Some(10), Ongoing)
    val nextState = Push.step(initState, b).unsafeRunSync()
    nextState.best shouldEqual Some(11)
    nextState.next shouldEqual Some(12)
    b.persist.offset shouldEqual 12
    b.forward.forwarded shouldEqual Seq(testBlock(11, "a"))

    b.rpc.nextBlock = "b"

    val endState = Push.step(nextState, b).unsafeRunSync()
    endState.best shouldEqual Some(12)
    endState.next shouldEqual Some(13)
    b.forward.forwarded shouldEqual
      Seq(testBlock(11, "a"), testBlock(12, "b"))
  }

  it should "in state ongoing, cope for missing blocks on subscription" in {
    val b = backend()

    b.rpc.bestBlock = 10
    b.rpc.nextBlock = "a"
    b.rpc.dataByHash =
      Map("a" -> testBlock(11, "a"), "e" -> testBlock(15, "e"))

    b.rpc.dataByHeight = Map(
      12L -> testBlock(12, "b"),
      13L -> testBlock(13, "c"),
      14L -> testBlock(14, "d"),
      15L -> testBlock(15, "e")
    )

    val initState = StateData(Some(11), Some(10), Ongoing)
    val nextState = Push.step(initState, b).unsafeRunSync()
    nextState.best shouldEqual Some(11)
    nextState.next shouldEqual Some(12)
    b.persist.offset shouldEqual 12
    b.forward.forwarded shouldEqual Seq(testBlock(11, "a"))

    b.rpc.nextBlock = "e"

    val intermediary = Push.step(nextState, b).unsafeRunSync()
    intermediary.currentState shouldEqual CatchUp
    intermediary.best shouldEqual Some(15)
    intermediary.next shouldEqual Some(12)
    b.persist.offset shouldEqual 12
    b.forward.forwarded shouldEqual Seq(testBlock(11, "a"))

    b.rpc.bestBlock = 15

    val endState =
      ExtractorRunners.runUntilExtractor(4, b, 0, intermediary).unsafeRunSync()

    endState.best shouldEqual Some(15)
    endState.next shouldEqual Some(16)
    endState.currentState shouldEqual Ongoing
  }
}
