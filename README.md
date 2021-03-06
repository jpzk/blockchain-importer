# blockchain-importer

[![Gitter chat](https://img.shields.io/badge/chat-on%20gitter-green)](https://gitter.im/blockchain-importer)
[![Travis](https://api.travis-ci.org/jpzk/blockchain-importer.svg?branch=master&status=passed)](https://travis-ci.org/github/jpzk/blockchain-importer)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/202ed1ef51524b749560c0ffd78400f7)](https://www.codacy.com/manual/jpzk/blockchain-importer?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jpzk/blockchain-importer&amp;utm_campaign=Badge_Grade)
[![License](http://img.shields.io/:license-Apache%202-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![GitHub stars](https://img.shields.io/github/stars/jpzk/blockchain-importer.svg?style=flat)](https://github.com/jpzk/bitcoin-importer/stargazers) 
<img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

*TLDR: Functional, typesafe, well-tested and composable streaming blockchain importer, drop-in solution for and leverages Kafka ecosystem. Aims for no-code and great developer experience*. 

**Blockchain-importer is the first-of-its-kind open-source software that enables typesafe agnostic real-time streaming of Blockchain data into different sinks and primiarily into the Kafka ecosystem** (in Avro and from here leveraging fault tolerance, exactly-once). It **currently supports Bitcoin**, but any block/transaction-based ledger can be added easily. It is therefore very different from the data science use case of Python/SQL-based analytics in AWS/GCP where other projects are more suited such as [bitcoin-etl](https://github.com/blockchain-etl/bitcoin-etl), [ethereum-etl](https://github.com/blockchain-etl/ethereum-etl). If you need to just read from the full node in a typesafe way, have a look at the [blockchain-rpc](https://github.com/jpzk/blockchain-rpc) project.

For any questions or support please visit our [gitter channel](https://gitter.im/blockchain-importer).

## Quickstart
The following commands will create a docker container setup with a Kafka broker, Schema registry, Zookeeper, PostgreSQL and the blockchain-importer. It will start ingesting blocks, transactions and scripts into Kafka topics starting from genesis block. Please change RPC settings in the docker-compose file minimal.yml to point to the RPC server your Bitcoin full node (with txindex=1 in config).

```
    # vim docker/minimal.yml (modify RPC settings)
    # docker-compose -f docker/minimal.yml up
```

## Table of Contents

1. [Modes](#modes)
2. [Avro Protocol](#avro-protocol)
3. [Configuration](#configuration)
4. [Running a full node](#running-a-full-node)
5. [Build your own Docker image](#building-your-own-docker-image)

## Modes

Blockchain-importer has two distinct modes **lagging** and **push**, **pushverify** which allows a consumer to select the type of subscription that is adequate for the existing streaming infrastructure. 

The lagging mode is widely used in lots of different Blockchain data companies and it maintains a distance between the current processed block and the tip of the chain. This approach ensures that wrong blocks due to blockchain reorganizations are not propagated downstream. The **push** mode is reading from the tip of the chain and is propagating the last mined block. This mode does not guarantee that only correct blocks are forwarded. Therefore, **pushverify** has been introduced which additionaly checks if there was a reorganization and replays the missed canonical blocks. 

### Modes cheatsheet

| mode | description                                                | use cases |
| -------------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| lagging | polling-based: maintains a distance between the current processed block and the tip of the chain | high-latency, store in data lake |
| push                      | push-based: reads from the tip of the chain, on each new mined block| low-latency, trading with onchain data, ignore reorgs |
| pushverify                      | like push, but also replays missed out blocks on reorganization | low-latency, mission critical, upsert correct data  |

## Avro Protocol

The Bitcoin protocol in blockchain-importer is specified in Avro as Block, Coinbase, Transaction, Input, Output and Script. The definition is [encoded as case classes in Scala using avro4s](https://github.com/jpzk/blockchain-importer/blob/master/src/main/scala/bitcoin/Bitcoin.scala).

## Configuration 

| variable                   | description                                                | type                                       |
| -------------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| BLOCKCHAIN                 | type of blockchain to extract                              | String                                     |
| MODE                       | push, pushverify or lagging                                | String                                     |
| LAG_BEHIND                 | if in lagging mode, sets lag behind                        | Integer                                    |
| POLL_TIME_MS               | if in lagging mode, sets poll time                         | Long                                       |
| BLOCKCHAIN_RPC_HOSTS       | Comma-seperated list of IPs or hostname of full nodes      | IP,IP                                      |
| BLOCKCHAIN_RPC_USERNAME    | RPC username                                               | String                                     |
| BLOCKCHAIN_RPC_PASSWORD    | RPC password                                               | String                                     |
| BLOCKCHAIN_RPC_PORT        | JSON-RPC port                                              | Optional Int                               |
| BLOCKCHAIN_RPC_ZEROMQ_PORT | ZeroMQ port                                                | Optional port                              |
| SCHEMA_REGISTRY            | Schema registry URL                                        | URL with port                              |
| BROKERS                    | Kafka broker list                                          | Comma seperated list of host:ip of brokers |
| KAFKA_TRANSACTIONAL_ID     | Transactional producer id                                  | String                                     |
| KAFKA_TOPIC_PREFIX         | Topic prefix for topics to push to \*-transactions         | String                                     |
| DB_HOST                    | Postgres host ip                                           | IP address                                 |
| DB_NAME                    | Postgres database name                                     | String                                     |
| DB_USER                    | Postgres user                                              | String                                     |
| DB_PASS                    | Postgres password                                          | String                                     |
| DB_OFFSET_NAME             | Extractor name under which store the offset                | String                                     |
| IN_MEMORY_OFFSET           | Temporary in-memory blockchain offset when running locally | String                                     |                             | String                                     |

## Running a full node

* Instead of AWS/GCP use a dedicated server provider, these are much cheaper. 
* Make sure your **chaindata is on a SSD**. Bootstrapping from genesis block is much faster.
* Make sure the RPC server is not reachable from the outside and set username/password.

## Building your own Docker image

```
    $ vim build.sbt (change organization name, top of file)
    $ sbt docker
```

