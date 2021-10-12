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

# TODOs
- Allow for when there's no schema

# Credits
Based heavily upon https://github.com/confluentinc/kafka-connect-insert-uuid and [Apache Kafka® `ReplaceField` SMT](https://github.com/apache/kafka/blob/trunk/connect/transforms/src/main/java/org/apache/kafka/connect/transforms/ReplaceField.java)
