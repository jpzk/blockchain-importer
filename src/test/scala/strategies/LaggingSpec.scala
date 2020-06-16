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

import com.madewithtea.blockchainimporter.BlockData
import org.scalatest.{FlatSpec, Matchers}
import scala.concurrent.duration._

class LaggingSpec extends FlatSpec with Matchers {
  import TestDependencies._

  behavior of "lagging extractor"

  it should "throw exception when called with state Ongoing but no state" in {
    val b = backend()
    assertThrows[Exception] {
      Lagging
        .step(StateData(None, None, Ongoing), 3, 0.seconds, b)
        .unsafeRunSync()
    }
  }

  it should "propagate RPC exceptions from backend" in {
    val b = backend(faultyRPC = true)
    assertThrows[Exception] {
      Lagging.step(StateData(), 3, 0.seconds, b).unsafeRunSync()
    }
  }

  it should "propagate persistence exceptions from backend" in {
    val b = backend(faultyPersistence = true)
    assertThrows[Exception] {
      Lagging.step(StateData(), 3, 0.seconds, b).unsafeRunSync()
    }
  }

  it should "propagate sink exceptions from backend" in {
    val b = backend(faultySink = true)
    b.rpc.bestBlock = 10
    b.persist.setOffset(1)
    assertThrows[Exception] {
      Lagging.step(StateData(), 3, 0.seconds, b).unsafeRunSync()
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

    val endState =
      ExtractorRunners.runUntilLaggingExtractor(11, 3, b).unsafeRunSync()
    endState.best shouldEqual Some(10)
    endState.next shouldEqual Some(8)
    endState.currentState shouldEqual CatchUp
  }
}
