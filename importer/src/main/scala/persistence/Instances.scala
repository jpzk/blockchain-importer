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

import cats.effect.Resource
import cats.effect.{IO, ContextShift}
import com.madewithtea.blockchainimporter._
import com.madewithtea.blockchainimporter.common.Context
import com.madewithtea.blockchainimporter.Persistence.Postgres

object Instances {
  implicit val memInstance = Resource.make(
    IO(
      new MemPersistence(Context.orThrow("IN_MEMORY_OFFSET").toLong): Persist[
        Persistence.MemPersistence
      ]
    )
  )(_ => IO.unit)

  implicit def postgresInstance(
      implicit cs: ContextShift[IO]
  ): Resource[IO, Persist[Persistence.Postgres]] =
    for {
      postgres <- Resource.make(
        for {
          psql <- IO(new PostgresSQL(PostgresSQLConfig.fromEnv))
          _ <- psql.ensureSchema() // create schema when not existent
        } yield psql
      )(psql => IO(psql.db.close())) // @todo type cast to avoid re-matching
      persist <- Resource.make(IO(new Persist[Persistence.Postgres] {
        def getOffset(): IO[Long] = postgres.getOffset()
        def setOffset(offset: Long): IO[Unit] = postgres.setOffset(offset)
        def getAllHeights(): IO[Seq[Long]] = postgres.getAllHeights()
        def removeForwardedHash(height: Long): IO[Unit] = postgres.removeForwardedHash(height)
        def getForwardedHash(height: Long): IO[Option[String]] =
          postgres.getForwardedHash(height)
        def setForwardedHash(height: Long, hash: String): IO[Unit] =
          postgres.setForwardedHash(height, hash)
      }))(_ => IO.unit)
    } yield persist
}
