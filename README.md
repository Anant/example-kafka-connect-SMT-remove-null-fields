Kafka Connect SMT to remove all keys/values in an avro record that have value of `null`.


## Use cases:
- Elasticsearch connector, so that it doesn't overwrite all unset fields with `null` values, which allows you to send partial records through without setting all unset values to `null`.

## Properties to configure:

|Name|Description|Type|Default|Importance|
|---|---|---|---|---|
||  |  |  |  |

(so far there's none)

## Example on how to add to your connector:
```
transforms=removenullfields
transforms.removenullfields.type=us.anant.kafka.connect.smt.RemoveNullFields$Value
```

# Testing/Development
## Setup in Docker-compose
```
# for ES
sysctl -w vm.max_map_count=262144

docker-compose up -d

##################
# setup kafka topic for the connector to consume from
docker-compose exec kafka-connect-smt-remove-null-fields_broker_1 kafka-topics --create --topic example-topic-avro --bootstrap-server broker:9092 --replication-factor 1 --partitions 1

# confirm topic creation
docker-compose exec kafka-connect-smt-remove-null-fields_broker_1 kafka-topics --list

##################
# setup kafka connect
##################
# build the SMT Jar
mvn clean package

# copy over the SMT Jar
docker cp ./target/RemoveNullFields-1.0-SNAPSHOT.jar kafka-connect-smt-remove-null-fields_kafka-connect-avro_1:/etc/kafka-connect/jars/

# restart connect worker
docker restart kafka-connect-smt-remove-null-fields_kafka-connect-avro_1

# setup ES connector
curl -XPOST http://localhost:8083/connectors/ -H "Content-Type: application/json" \
-d '{"name": "elasticsearch-sink", "config": {
    "connection.url": "http://smt-test-es01:9200",
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "name": "elasticsearch-sink",
    "tasks.max": "1",
    "topics": "example-topic-avro",
    "transforms": "ValueToKey,ExtractFieldKey,dropPrefix,RemoveNullFields",
    "transforms.ExtractFieldKey.field": "id",
    "transforms.ExtractFieldKey.type": "org.apache.kafka.connect.transforms.ExtractField$Key",
    "transforms.ValueToKey.fields": "id",
    "transforms.ValueToKey.type": "org.apache.kafka.connect.transforms.ValueToKey",
    "transforms.RemoveNullFields.type": "us.anant.kafka.connect.smt.RemoveNullFields$Value",
    "transforms.dropPrefix.regex": ".*",
    "transforms.dropPrefix.replacement": "index_1",
    "transforms.dropPrefix.type": "org.apache.kafka.connect.transforms.RegexRouter",
    "type.name": "_doc",
    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url": "http://schema-registry:8081",
    "write.method": "upsert"
}
}'

# check status
curl localhost:8083/connectors/elasticsearch-sink/status
```

## Send some records

```
# start producer
docker exec -it kafka-connect-smt-remove-null-fields_schema-registry_1 kafka-avro-console-producer \
--topic example-topic-avro \
--bootstrap-server broker:9092 \
--property value.schema="$(< ./testing/location.avsc)"
```

Then in the producer, send some records, e.g., 
```
{"id":"8","address_line1":{"string": "400 8th st."},"address_line2":{"string": "# 400"},"zipcode":{"string": "97000"},"city":{"string": "Portland"},"country":{"string": "U.S.A."},"county":{"string": "Jefferson"},"latitude":{"string": "-25"},"longitude":{"string": "23"},"state":{"string": "CA"}}
{"id":"9","address_line1":{"string": "400 8th st."},"address_line2":{"string": "# 400"},"zipcode":{"string": "97000"},"city":{"string": "Portland"},"country":{"string": "U.S.A."},"county":{"string": "Jefferson"},"latitude":{"string": "-25"},"longitude":{"string": "23"},"state":null}
```

## Check result in ES
```
curl localhost:9200/index_1/_search?pretty

```
#### Result after initial insert:
```
{
        "_index" : "index_1",
        "_type" : "_doc",
        "_id" : "8",
        "_score" : 1.0,
        "_source" : {
          "id" : "8",
          "address_line1" : "400 8th st.",
          "address_line2" : "# 400",
          "city" : "Portland",
          "country" : "U.S.A.",
          "county" : "Jefferson",
          "latitude" : "-25",
          "longitude" : "23",
          "state" : "CA",
          "zipcode" : "97000"
        }
      },
      {
        "_index" : "index_1",
        "_type" : "_doc",
        "_id" : "9",
        "_score" : 1.0,
        "_source" : {
          "id" : "9",
          "address_line1" : "400 8th st.",
          "address_line2" : "# 400",
          "city" : "Portland",
          "country" : "U.S.A.",
          "county" : "Jefferson",
          "latitude" : "-25",
          "longitude" : "23",
          "zipcode" : "97000"
        }
      }
```

## Then send partial record, and see what gets overwritten and what doesn't
```
{"id":"8","address_line1":null,"address_line2":null,"city":null,"country":null,"county":{"string": "Lincoln"},"longitude":{"string": "-10.3"},"latitude":null,"state":null,"zipcode":{"string": "12345"}}
{"id":"9","address_line1":null,"address_line2":null,"city":{"string": "Los Angeles"},"country":null,"county":{"string": "Lincoln"},"longitude":{"string": "-10.3"},"latitude":null,"state":null,"zipcode":null}
```

This should overwrite ONLY fields that are not set as `null`, and leave alone all other fields. 

#### Result after upsert:
```
{
        "_index" : "index_1",
        "_type" : "_doc",
        "_id" : "8",
        "_score" : 1.0,
        "_source" : {
          "id" : "8",
          "address_line1" : "400 8th st.",
          "address_line2" : "# 400",
          "city" : "Portland",
          "country" : "U.S.A.",
          "county" : "Lincoln",
          "latitude" : "-25",
          "longitude" : "-10.3",
          "state" : "CA",
          "zipcode" : "12345"
        }
      },
      {
        "_index" : "index_1",
        "_type" : "_doc",
        "_id" : "9",
        "_score" : 1.0,
        "_source" : {
          "id" : "9",
          "address_line1" : "400 8th st.",
          "address_line2" : "# 400",
          "city" : "Los Angeles",
          "country" : "U.S.A.",
          "county" : "Lincoln",
          "latitude" : "-25",
          "longitude" : "-10.3",
          "zipcode" : "97000"
        }
      }
```

# Credits
Based heavily upon https://github.com/confluentinc/kafka-connect-insert-uuid and [Apache Kafka® `ReplaceField` SMT](https://github.com/apache/kafka/blob/trunk/connect/transforms/src/main/java/org/apache/kafka/connect/transforms/ReplaceField.java)
