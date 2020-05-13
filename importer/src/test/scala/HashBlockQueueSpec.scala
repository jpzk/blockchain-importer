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

import com.madewithtea.blockchainimporter.{Persist, Persistence}
import com.madewithtea.blockchainimporter.persistence._
import org.scalatest.{FlatSpec, Matchers}
import scala.concurrent.duration._

import com.madewithtea.blockchainimporter.persistence._
import cats.effect.IO

class HashBlockQueueSpec extends FlatSpec with Matchers {
  import TestDependencies._

  behavior of "limited hash block queue"

  it should "fill if empty" in { 
    val b = backend()
    b.rpc.dataByHeight = createChainByHeight(0, 10)
    val q = HashBlockQueue.limit(b.persist, 3)
    q.fillIfEmpty(9, b.rpc).unsafeRunSync()
    val t = for {
      a <- q.get(9)
      b <- q.get(8)
      c <- q.get(7)
    } yield (a, b, c)
    t.unsafeRunSync() shouldEqual
      (Some("hash-of-9"), Some("hash-of-8"), Some("hash-of-7"))
  }

  it should "fill queue ensure limit" in {
    val b = backend()
    b.rpc.dataByHeight = createChainByHeight(0, 10)
    val q = HashBlockQueue.limit(b.persist, 3)
    val p = b.persist
    val t = for {
      _ <- q.fillIfEmpty(9, b.rpc)
      _ <- q.append(6, "hash-of-6")
      a <- q.get(9)
      b <- q.get(8)
      c <- q.get(7)
      heights <- p.getAllHeights()
    } yield (a, b, c, heights)
    t.unsafeRunSync() shouldEqual
      (Some("hash-of-9"), Some("hash-of-8"), Some("hash-of-7"),
      Seq(9,8,7))
  }

  it should "append new hash on height" in {
    val p = new MemPersistence(0L): Persist[
      Persistence.MemPersistence
    ]
    val q = HashBlockQueue.limit(p, 3)
    val io = for {
      _ <- q.append(0, "hash-of-0")
      hash <- q.get(0)
    } yield hash
    io.unsafeRunSync() shouldEqual Some("hash-of-0")
  }

  it should "append new hash on height, ensure trimming to limit" in {
    val p = new MemPersistence(0L): Persist[
      Persistence.MemPersistence
    ]
    val q = HashBlockQueue.limit(p, 2)
    val io = for {
      _ <- q.append(0, "hash-of-0")
      _ <- q.append(1, "hash-of-1")
      _ <- q.append(2, "hash-of-2")
      heights <- p.getAllHeights()
    } yield heights
    io.unsafeRunSync() shouldEqual Seq(1, 2)
  }

}
