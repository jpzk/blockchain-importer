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

import java.util.Properties

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.serializers.{
  AbstractKafkaAvroSerDeConfig,
  KafkaAvroSerializer
}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{
  KafkaProducer,
  ProducerConfig,
  ProducerRecord
}
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors._
import org.apache.kafka.common.serialization.LongSerializer

import com.madewithtea.blockchainimporter.{Forward, BlockData}
import com.madewithtea.blockchainimporter.{Sink}
import com.madewithtea.blockchainimporter.Context

class KafkaSink(blockchain: String, brokers: String, schemaRegistry: String)
    extends Forward[Sink.Kafka] with LazyLogging
    {

  var producer: Option[KafkaProducer[Long, GenericRecord]] = None

  // Kafka producer setup
  val props = new Properties
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
  props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")

  // this has to e different for each producer for each blockchain
  props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, Context.orThrow("KAFKA_TRANSACTIONAL_ID"))
  props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[LongSerializer])
  props.put(
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
    classOf[KafkaAvroSerializer]
  )
  props.put(
    AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
    schemaRegistry
  )

  val p = new KafkaProducer[Long, GenericRecord](props)
  p.initTransactions()
  producer = Some(p)

  def forward(blockData: BlockData): IO[Unit] = IO {
    val p = producer.getOrElse(throw new Exception("Producer not initialized"))

    p.beginTransaction()
    try {
      blockData.topicsAndRecords.foreach {
        case (topic, records) =>
          // send records for each topic
          val topicName = s"${Context.orThrow("KAFKA_TOPIC_PREFIX")}-$topic"
          records.foreach { record =>
            val r = new ProducerRecord(topicName, blockData.blockNumber, record)
            p.send(r)
          }
      }
    } catch {
      case e @ (_: ProducerFencedException | _: OutOfOrderSequenceException |
          _: AuthorizationException) =>
        // We can't recover from these exceptions, so our only option is to close
        // the producer and exit.
        p.close()
        logger.error(
          s"Got $e, can't recover, will kill VM and abort transaction"
        )

        // kill JVM with error code.
        sys.exit(-1);

      case e: KafkaException =>
        // For all other exceptions, just abort the transaction and try again.
        p.abortTransaction()

        // will retry
        throw e;
    }

    // ONLY commits if all sends were sent.
    p.commitTransaction()
  }

  def close() = IO {
    producer.foreach { p =>
      p.abortTransaction()
      p.close()
    }
  }
}
