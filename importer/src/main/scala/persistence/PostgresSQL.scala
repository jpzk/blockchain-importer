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
import com.madewithtea.blockchainimporter.Context

case class PostgresSQLConfig(
    host: String,
    name: String,
    user: String,
    pass: String,
    offsetName: String
)

object PostgresSQLConfig {
  def fromEnv = {
    PostgresSQLConfig(
      Context.orThrow("DB_HOST"),
      Context.orThrow("DB_NAME"),
      Context.orThrow("DB_USER"),
      Context.orThrow("DB_PASS"),
      Context.orThrow("DB_OFFSET_NAME")
    )
  }
}

class PostgresSQL(config: PostgresSQLConfig)(implicit cs: ContextShift[IO]) {
  val configs = TableQuery[ExtractorConfigTable]
  val hashes = TableQuery[BlocksHashesTable]

  val db = Database.forDriver(
    new org.postgresql.Driver,
    s"jdbc:postgresql://${config.host}/${config.name}",
    config.user,
    config.pass
  )

  val forBlockchain = configs.filter(_.name === config.offsetName)
  val offsetQuery = forBlockchain.map(_.offset)
  val batchSizeQuery = forBlockchain.map(_.batchSize)
  val nodesQuery = forBlockchain.map(_.nodes)
  val hashesForBlockchain = hashes.filter(_.name === config.name)
  def hashQuery(height: Long) =
    hashesForBlockchain.filter(_.height === height)

  def ensureSchema(): IO[Unit] =
    IO.fromFuture(
      IO(
        db.run(
          DBIO.seq(
            configs.schema.createIfNotExists,
            hashes.schema.createIfNotExists
          )
        )
      )
    )

  // @todo put offset 0 in ensureSchema when configuration not found
  def getOffset(): IO[Long] =
    IO.fromFuture(IO(db.run(offsetQuery.result).map { r =>
      if (r.isEmpty) throw new Exception("Offset not found.") else r.head
    }))

  def setOffset(offset: Long): IO[Unit] =
    IO.fromFuture(IO(db.run(offsetQuery.update(offset)).map(_ => ())))

  def getForwardedHash(height: Long): IO[Option[String]] =
    IO.fromFuture(IO(db.run(hashQuery(height).map(_.hash).result)))
      .map(_.headOption)

  def setForwardedHash(height: Long, hash: String): IO[Unit] =
    IO.fromFuture(
      IO(db.run(hashes += (config.offsetName, height, hash)).map(_ => ()))
    )

  def removeForwardedHash(height: Long): IO[Unit] =
    IO.fromFuture(
      IO(db.run(hashQuery(height).delete).map(_ => ()))
    )

  def getAllHeights(): IO[Seq[Long]] =
    IO.fromFuture(IO(db.run(hashesForBlockchain.map(_.height).result)))
}

class ExtractorConfigTable(tag: Tag)
    extends Table[(String, Long, Int, String)](tag, "configs") {
  def name = column[String]("name", O.PrimaryKey)

  def offset = column[Long]("offset")

  def batchSize = column[Int]("batchsize")

  def nodes = column[String]("nodes")

  def * = (name, offset, batchSize, nodes)
}

class BlocksHashesTable(tag: Tag)
    extends Table[(String, Long, String)](tag, "block_hashes") {
  def name = column[String]("name")

  def height = column[Long]("height")

  def hash = column[String]("hash")

  def pk = primaryKey("pk_a", (name, height))

  def * = (name, height, hash)
}
