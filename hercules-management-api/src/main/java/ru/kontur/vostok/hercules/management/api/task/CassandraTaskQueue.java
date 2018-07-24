package ru.kontur.vostok.hercules.management.api.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import ru.kontur.vostok.hercules.kafka.util.serialization.VoidSerializer;
import ru.kontur.vostok.hercules.management.task.cassandra.CassandraTask;
import ru.kontur.vostok.hercules.management.task.cassandra.CassandraTaskType;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Gregory Koshelev
 */
public class CassandraTaskQueue {
    private final KafkaProducer<Void, byte[]> producer;
    private final ObjectWriter serializer;

    public CassandraTaskQueue(Properties properties) {
        this.producer = new KafkaProducer<>(properties, new VoidSerializer(), new ByteArraySerializer());

        ObjectMapper objectMapper = new ObjectMapper();
        this.serializer = objectMapper.writerFor(CassandraTask.class);
    }

    public void createTable(String table) {
        sendTask(new CassandraTask(table, CassandraTaskType.CREATE));
    }

    public void deleteTable(String table) {
        sendTask(new CassandraTask(table, CassandraTaskType.DELETE));
    }

    public void close(long timeout, TimeUnit unit) {
        producer.close(timeout, unit);
    }

    private void sendTask(CassandraTask task) {
        try {
            byte[] value = serializer.writeValueAsBytes(task);
            ProducerRecord<Void, byte[]> record = new ProducerRecord<>("hercules_management_cassandra", value);
            Future<RecordMetadata> result = producer.send(record);
            result.get(5_000, TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException | InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot send task", e);
        }
    }
}