# blockchain-importer
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/202ed1ef51524b749560c0ffd78400f7)](https://www.codacy.com/manual/jpzk/blockchain-importer?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jpzk/blockchain-rpc&amp;utm_campaign=Badge_Grade)
[![License](http://img.shields.io/:license-Apache%202-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![GitHub stars](https://img.shields.io/github/stars/jpzk/blockchain-importer.svg?style=flat)](https://github.com/jpzk/bitcoin-importer/stargazers) 
[![Maven Central](https://img.shields.io/maven-central/v/com.madewithtea/blockchain-importer_2.13.svg)](https://search.maven.org/search?q=com.madewithtea%20blockchain-importer) <img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

*TLDR: Functional, typesafe, well-tested and composable streaming blockchain importer, drop-in solution for and leverages Kafka ecosystem. Aims for no-code and great developer experience*. 

**Blockchain-importer is the first-of-its-kind open-source software that enables typesafe agnostic real-time streaming of Blockchain data into different sinks and primiarily into the Kafka ecosystem** (in Avro and from here leveraging fault tolerance, exactly-once). It *currently supports Bitcoin*, but any block/transaction-based ledger can be added easily. It is therefore very different from the data science use case of Python/SQL-based analytics in AWS/GCP where other projects are more suited such as [bitcoin-etl](https://github.com/blockchain-etl/bitcoin-etl), [ethereum-etl](https://github.com/blockchain-etl/ethereum-etl). If you need to just read from the full node in a typesafe way, have a look at the [blockchain-rpc](https://github.com/jpzk/blockchain-rpc) project.

## Quickstart
```
    # vim docker/minimal.yml (modify RPC settings)
    # docker-compose -f docker/minimal.yml up
```

## Modes

Blockchain-importer has two distinct modes **lagging** and **push**, **pushverify** which allows a consumer to select the type of subscription that is adequate for the existing streaming infrastructure. The lagging mode is widely used in lots of different Blockchain data companies and it maintains a distance between the current processed block and the tip of the chain. This approach ensures that wrong blocks due to blockchain reorganizations are not propagated downstream. The **push** mode is reading from the tip of the chain and is propagating the last mined block. This mode does not guarantee that only correct blocks are forwarded. Therefore, **pushverify** has been introduced which additionaly checks if there was a reorganization and replays the missed canonical blocks. 

### Modes cheatsheet

| mode | description                                                | use cases |
| -------------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| lagging | polling-based: maintains a distance between the current processed block and the tip of the chain | high-latency, store in data lake |
| push                      | push-based: reads from the tip of the chain, on each new mined block| low-latency, trading with onchain data, ignore reorgs |
| pushverify                      | like push, but also replays missed out blocks on reorganization | low-latency, mission critical, upsert correct data  |

## Fast-experimentation or Evaluation

For fast experimentation and without any compilation (no-code) or installing necessary infrastructure (e.g. Kafka, PostgreSQL etc.), we've crafted a [docker-compose](https://docs.docker.com/compose/) file which lets you run everything necessary with one command. It will start ingesting blocks, transactions and scripts into the specified Kafka topics, starting from block 0. Only a bitcoin full node is required, for this you need to modify the [docker/minimal.yml](https://github.com/jpzk/blockchain-importer/blob/master/docker/minimal.yml) file. 

```
    # vim docker/minimal.yml (modify RPC settings)
    # docker-compose -f docker/minimal.yml up
```

## Requirements for production

1. Bitcoin full node with txindex=1 
2. Kafka ecosystem, brokers, zookeeper, schema-registry 
3. For storing the current processed block height and block hashes (used for *push verify* mode) the importer needs a PostgresSQL database

## Environment flags

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
