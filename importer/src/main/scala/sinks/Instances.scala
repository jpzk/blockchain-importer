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
package com.madewithtea.blockchainimporter.sinks

import cats.effect.Resource
import cats.effect.IO
import com.madewithtea.blockchainimporter.{BlockData, Forward, Sink}
import com.madewithtea.blockchainimporter.Context
import com.typesafe.scalalogging.LazyLogging

object Instances {
  implicit val loggingInstance
      : Resource[IO, Forward[Sink.LoggingOutput]] =
    Resource.make(
      IO(
        new Forward[Sink.LoggingOutput] {
          def forward(record: BlockData): IO[Unit] =
            IO(println(record.blockNumber))
        }
      )
    )(_ => IO.unit)

  implicit val kafkaInstance = for {
    kafka <- Resource.make(
      IO(
        new KafkaSink(
          Context.blockchain,
          Context.brokers,
          Context.schemaRegistry
        )
      )
    )(kafka => kafka.close())
    forward <- Resource.make(IO(new Forward[Sink.Kafka] {
      def forward(b: BlockData): IO[Unit] = {
        kafka.forward(b)
      }
    }))(_ => IO.unit)
  } yield forward 
}
