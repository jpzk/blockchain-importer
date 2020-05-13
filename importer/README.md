# blockchain-importer

## Environment flags

| variable                   | description                                                | type                                       |
| -------------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| BLOCKCHAIN                 | type of blockchain to extrace                              | String                                     |
| MODE                       | push or lagging                                            | String                                     |
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
