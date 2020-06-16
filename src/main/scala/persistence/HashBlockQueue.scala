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

import cats._
import cats.implicits._
import cats.syntax._
import cats.effect.IO
import cats.effect._

import com.madewithtea.blockchainimporter._
import com.typesafe.scalalogging.LazyLogging

trait HashBlockLimitedQueue {
  def fillIfEmpty[A <: Blockchain](current: Long, rpc: RPC[A]): IO[Unit]
  def append(height: Long, hash: String): IO[Unit]
  def get(height: Long): IO[Option[String]]
  def verify[A <: Blockchain](
      block: BlockData
  ): IO[Boolean]
}

object HashBlockQueue extends LazyLogging {

  def limit[E <: Persistence](e: Persist[E], maxEntries: Int) =
    new HashBlockLimitedQueue {
      val MaxEntries = maxEntries

      def fillIfEmpty[A <: Blockchain](current: Long, rpc: RPC[A]): IO[Unit] =
        for {
          heights <- e.getAllHeights()
          action <- if (heights.size < MaxEntries) fillQueue(current, rpc)
          else IO.unit
        } yield action

      def append(height: Long, hash: String): IO[Unit] =
        for {
          _ <- e.setForwardedHash(height, hash)
          heights <- e.getAllHeights()
          delete <- IO.pure(markedDeleted(heights.toList))
          _ <- delete.map(e.removeForwardedHash).sequence
        } yield ()

      def get(height: Long): IO[Option[String]] =
        e.getForwardedHash(height)

      def fillQueue[A <: Blockchain](
          current: Long,
          rpc: RPC[A],
          amount: Long = MaxEntries
      ): IO[Unit] = (current, amount) match {
        case (c, a) if c == 0 || a == 0 => IO.unit
        case (c, a)                     => fillQueueRecurse(c, a, rpc)
      }

      def fillQueueRecurse[A <: Blockchain](
          current: Long,
          amount: Long,
          rpc: RPC[A]
      ): IO[Unit] =
        for {
          hash <- get(current)
          loop <- hash match {
            case None =>
              for {
                data <- rpc.getDataByHeight(current)
                _ <- append(current, data.hash)
                recurse <- fillQueue(current - 1, rpc, amount - 1)
              } yield recurse
            case Some(_) =>
              fillQueue(current - 1, rpc, amount - 1)
          }
        } yield loop

      def verify[A <: Blockchain](
          block: BlockData
      ): IO[Boolean] =
        if (block.blockNumber == 0L)
          return IO.pure(true)
        else
          for {
            stored <- get(block.blockNumber - 1)
          } yield (stored, block.previousHash) match {
            case (Some(s), Some(p)) =>
              if (s == p) true else false
            case (None, _) =>
              throw new Exception(
                s"Previous hash of blocknumber ${block.blockNumber - 1}"
                  + " not stored for verify."
              )
            case (_, None) =>
              throw new Exception("Parent hash not in block data.")
            case _ =>
              throw new Exception("Previous hash / parent hash not available.")
          }

      private def markedDeleted(heights: List[Long]): List[Long] = {
        val sort = heights.sorted
        val size = sort.size
        val offset = (size - maxEntries)
        if (offset > 0)
          sort.take(offset)
        else
          List()
      }
    }
}
