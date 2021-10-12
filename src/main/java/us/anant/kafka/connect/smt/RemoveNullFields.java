/*
 * Copyright © 2021 Anant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package us.anant.kafka.connect.smt;

import org.apache.kafka.common.cache.Cache;
import org.apache.kafka.common.cache.LRUCache;
import org.apache.kafka.common.cache.SynchronizedCache;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SchemaUtil;
import org.apache.kafka.connect.transforms.util.SimpleConfig;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

public abstract class RemoveNullFields<R extends ConnectRecord<R>> implements Transformation<R> {

  public static final String OVERVIEW_DOC =
    "Removes all keys and values for any field with value of null in each record";

  // TODO probably remove, since this SMT doesn't have any configurable settings
  private interface ConfigName {
    //String UUID_FIELD_NAME = "uuid.field.name";
  }

  // TODO probably remove, since this SMT doesn't have any configurable settings
  public static final ConfigDef CONFIG_DEF = new ConfigDef();
    //.define(ConfigName.UUID_FIELD_NAME, ConfigDef.Type.STRING, "uuid", ConfigDef.Importance.HIGH,
    //  "Field name for UUID");

  private static final String PURPOSE = 
    "Removes all keys and values for any field with value of null in each record";

  private Cache<Schema, Schema> schemaUpdateCache;

  @Override
  public void configure(Map<String, ?> props) {
    // NOTE not using right now, can get rid of
    final SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);

    schemaUpdateCache = new SynchronizedCache<>(new LRUCache<Schema, Schema>(16));
  }


  @Override
  public R apply(R record) {
    // for now, just assuming a schema
    //if (operatingSchema(record) == null) {
    //  return applySchemaless(record);
    //} else {
      return applyWithSchema(record);
    //}
  }

  // for now, just assuming a schema
  // private R applySchemaless(R record) {
  //   final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);

  //   final Map<String, Object> updatedValue = new HashMap<>(value);

  //   updatedValue.put(fieldName, getRandomUuid());

  //   return newRecord(record, null, updatedValue);
  // }

  private R applyWithSchema(R record) {
    final Struct value = requireStruct(operatingValue(record), PURPOSE);


    // TODO need to look into best way to do this, for best performance. Manually creating schema each time might really slow things down
    // I'm not yet sure how the cache works, or what exactly is returned wshen using e.g., value.schema()
    // for now, not using schema caching, since I'm not sure how we'd do that with a dynamic schema like this. I'm not sure if any other SMT has schema that could potentially change with every record like ours does. 
    Schema existingSchema = value.schema();

    // Schema updatedSchema = schemaUpdateCache.get(value.schema());
		// if (updatedSchema == null) {
		// 		updatedSchema = makeUpdatedSchema(value.schema());
		// 		schemaUpdateCache.put(value.schema(), updatedSchema);
		// }

    final SchemaBuilder builder = SchemaUtil.copySchemaBasics(existingSchema, SchemaBuilder.struct());

    final Struct updatedValue = new Struct(existingSchema);

    System.out.println("\n");
    System.out.println("=================");
    for (Field field : existingSchema.fields()) {
      // check if value is null. If null, don't set field in record or schema
      // If value not null, set field to new record without changing
      if (value.get(field) != null) {
        // set key and value for field on this record
        updatedValue.put(field.name(), value.get(field));
        // set field on schema also
        builder.field(field.name(), field.schema());
        System.out.println(field.name() + ": " + value.get(field).toString());
      } else {
        System.out.println(field.name() + ": " + "<null>");
        //testing code. 
        //adding this in brings us back to original behavior: everything erased except for fields that are explicitly set. 
        //builder.field(field.name(), field.schema());
      }
    }
    System.out.println("=================");

    Schema updatedSchema = builder.build();
    System.out.println("\nupdated schema fields: " + updatedSchema.fields().toString());


    R updatedRecord = newRecord(record, updatedSchema, updatedValue);
    // gives too much info
    //System.out.println("\nrecord: " + updatedRecord.toString());
    System.out.println("\nrecord value: " + updatedRecord.value().toString());
    System.out.println("\nrecord schema fields: " + updatedRecord.valueSchema().fields().toString());
    return updatedRecord;
  }

  @Override
  public ConfigDef config() {
    return CONFIG_DEF;
  }

  /**
   * TODO determine what this is for
   *
   */ 
  @Override
  public void close() {
    schemaUpdateCache = null;
  }

  /*
   * update the avro schema for this record
   *
   * TODO might not need to do this, since this is just for sinking to ES, which should not check the schema I don't think. 
   * Or if we do this, then don't need to check fields again when doing applyWithSchema method
   */ 
  private Schema makeUpdatedSchema(Schema schema) {
    final SchemaBuilder builder = SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct());

    for (Field field: schema.fields()) {
      // check if value is null. If null, don't set field in avro schema. 
      // NOTE currently try without changing schema
      // if this works, don't bother using makeUpdatedSchema, just send original schema.
      // If value not null, set field on schema without changing
      //if (schema.get(field) != null) {
        builder.field(field.name(), field.schema());
      //}
    }

    return builder.build();
  }

  protected abstract Schema operatingSchema(R record);

  protected abstract Object operatingValue(R record);

  protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

  /**
   * TODO I believe that this is only used if this SMT is applied to a Kafka record's key. (per this blog post: https://www.confluent.io/blog/kafka-connect-single-message-transformation-tutorial-with-examples/)
   * Accordingly, probably can just remove this Key Subclass
   */ 
  public static class Key<R extends ConnectRecord<R>> extends RemoveNullFields<R> {

    @Override
    protected Schema operatingSchema(R record) {
      return record.keySchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.key();
    }

    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), updatedSchema, updatedValue, record.valueSchema(), record.value(), record.timestamp());
    }

  }

  /**
   * since we are following an example that allowed the example SMT to be applied to key or value, the ConnectRecord<R> class has an abstract field operationSchema and operatingValue that needs to be implemented. 
   * Accordingly, we need this subclass (Value<R>) to actually implement these fields. We could just implement this in the main class and specify use fo the main class instead of this subclass in property `transforms.removenullfields.type` later but for now, just keeping as close to example as possible.
   *
   */ 
  public static class Value<R extends ConnectRecord<R>> extends RemoveNullFields<R> {

    @Override
    protected Schema operatingSchema(R record) {
      return record.valueSchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.value();
    }

    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(), updatedSchema, updatedValue, record.timestamp());
    }

  }
}


