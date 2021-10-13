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
# setup kafka topic 
docker-compose exec kafka-connect-smt-remove-null-fields_broker_1 kafka-topics --create --topic example-topic-avro --bootstrap-server broker:9092 --replication-factor 1 --partitions 1

# confirm
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
#### Current (incorrect) behavior:
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
          "zipcode" : null
        }
      }
```
For whatever reason, any record that has one or more fields not set, that field is not set (which is right), but zipcode also doesn't work (e.g., record with id=9 above)

## Then send partial record, and see what gets overwritten and what doesn't
```
{"id":"8","address_line1":null,"address_line2":null,"city":null,"country":null,"county":{"string": "Lincoln"},"longitude":{"string": "-10.3"},"latitude":null,"state":null,"zipcode":{"string": "12345"}}
{"id":"9","address_line1":null,"address_line2":null,"city":{"string": "Los Angeles"},"country":null,"county":{"string": "Lincoln"},"longitude":{"string": "-10.3"},"latitude":null,"state":null,"zipcode":null}
```

This should overwrite ONLY fields that are not set as `null`, and leave alone all other fields. 

#### Current (incorrect) behavior:
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
          "county" : null,
          "latitude" : "-25",
          "longitude" : null,
          "state" : "CA",
          "zipcode" : null
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
          "city" : null,
          "country" : "U.S.A.",
          "county" : null,
          "latitude" : "-25",
          "longitude" : "Los Angeles",
          "zipcode" : null
        }
      }
```

Instead, it should be only changing fields that were sent with a non-`null` value. And for some reason, record #9 has longitude instead of city being set. 

This despite logging from Kafka Connect worker on what's being sent as a record indicating that keys and values seem to be correct:
```
docker logs -f kafka-connect-smt-remove-null-fields_kafka-connect-avro_1 --since 5m
```

> id: 10
> address_line1: <null>
> address_line2: <null>
> city: Los Angeles
> country: <null>
> county: Lincoln
> latitude: <null>
> longitude: -10.3
> state: <null>
> zipcode: <null>
> 
> updated schema fields: [Field{name=id, index=0, schema=Schema{STRING}}, Field{name=city, index=1, schema=Schema{STRING}}, Field{name=county, index=2, schema=Schema{STRING}}, Field{name=longitude, index=3, schema=Schema{STRING}}]
> 
> record value: Struct{id=10,city=Los Angeles,county=Lincoln,longitude=-10.3}
> 
> record schema fields: [Field{name=id, index=0, schema=Schema{STRING}}, Field{name=city, index=1, schema=Schema{STRING}}, Field{name=county, index=2, schema=Schema{STRING}}, Field{name=longitude, index=3, schema=Schema{STRING}}]

## Check Schemas that get created
#### Find All subjects:
```
# port 8081 of schema registry is exposed, so you can call from docker host
curl localhost:8081/subjects
```
Result should be: 
> ["example-topic-avro-value"]

#### Find schemas that get created
```
# get all version ids
curl localhost:8081/subjects/example-topic-avro-value/versions/

# using example given above, there should only be one
curl localhost:8081/subjects/example-topic-avro-value/versions/1
```

Result should be: 
> {"subject":"example-topic-avro-value","version":1,"id":1,"schema":"{\"type\":\"record\",\"name\":\"location\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"address_line1\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"address_line2\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"city\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"country\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"county\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"latitude\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"longitude\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"state\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"zipcode\",\"type\":[\"null\",\"string\"],\"default\":null}]}"}

In other words, schema in schema registry should not be affected by this SMT - or any sink SMT! This only changes what gets sent along

# Credits
Based heavily upon https://github.com/confluentinc/kafka-connect-insert-uuid and [Apache Kafka® `ReplaceField` SMT](https://github.com/apache/kafka/blob/trunk/connect/transforms/src/main/java/org/apache/kafka/connect/transforms/ReplaceField.java). At this point, more the latter than the former. 
