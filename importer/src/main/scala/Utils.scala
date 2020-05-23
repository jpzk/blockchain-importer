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
package com.madewithtea.blockchainimporter

import org.apache.commons.codec.binary.Hex
import scala.util.Try

object HexTools {
  def hex2bytes(hex: String): Array[Byte] = Hex.decodeHex(hex)

  def bytes2hex(bytes: Array[Byte]): String = Hex.encodeHex(bytes).mkString
}


object Context {
  def orThrow(name: String): String =
    sys.env.get(name).getOrElse(throw new Exception(s"Specify $name"))

  lazy val blockchain: String = orThrow("BLOCKCHAIN")
  lazy val brokers = orThrow("BROKERS")
  lazy val schemaRegistry = orThrow("SCHEMA_REGISTRY")
  lazy val metrics = orThrow("GRAPHITE")
}

object Avro {
  import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
  import com.sksamuel.avro4s._

  def to[A: SchemaFor: Encoder](state: A): Array[Byte] = {
    val schema = AvroSchema[A]
    val os = new ByteArrayOutputStream()
    val aos = AvroOutputStream.binary[A].to(os).build(schema)
    aos.write(state)
    aos.flush()
    aos.close()
    os.toByteArray
  }

  def from[A: SchemaFor: Decoder](bytes: Array[Byte]): Try[A] = {
    val schema = AvroSchema[A]
    val is = new ByteArrayInputStream(bytes)
    val aos = AvroInputStream.binary[A].from(is).build(schema)
    aos.tryIterator.next()
  }
}