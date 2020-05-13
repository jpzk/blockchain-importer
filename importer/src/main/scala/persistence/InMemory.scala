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
package com.madewithtea.blockchainimporter.persistence

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.{ContextShift, IO, Resource}

import com.madewithtea.blockchainimporter.Persist
import com.madewithtea.blockchainimporter.Persistence

class MemPersistence(startOffset: Long) 
  extends Persist[Persistence.MemPersistence] with LazyLogging {
    
    var offset: Long = startOffset;
    var hashes: Map[Long, String] = Map()

    def getAllHeights(): IO[Seq[Long]]  = IO(hashes.keySet.toSeq)

    def getForwardedHash(height: Long): IO[Option[String]] = IO(hashes.get(height))

    def setForwardedHash(height: Long, hash: String): IO[Unit] = IO {
      this.hashes = this.hashes + (height -> hash)
    }
    def removeForwardedHash(height: Long): IO[Unit] = IO {
      this.hashes = this.hashes - height
    }

    def setOffset(offset: Long): IO[Unit] =
      for {
        _ <- IO(logger.debug(s"Setting offset to $offset"))
        _ <- IO {
          this.offset = offset
        }
      } yield ()
      
    def getOffset(): IO[Long] =
      for {
        _ <- IO(logger.debug(s"getOffset, current offset is ${this.offset}"))
      } yield this.offset
  }