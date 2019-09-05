package ru.kontur.vostok.hercules.protocol.util;

import ru.kontur.vostok.hercules.protocol.Container;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.protocol.Variant;
import ru.kontur.vostok.hercules.protocol.encoder.ContainerWriter;
import ru.kontur.vostok.hercules.protocol.encoder.Encoder;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * Simple Event build. NOT thread-safe
 */
public class EventBuilder {

    private static final ContainerWriter CONTAINER_WRITER = new ContainerWriter();

    private long timestamp;
    private UUID random;
    private int version;
    private ContainerBuilder containerBuilder = ContainerBuilder.create();

    private boolean wasBuild = false;

    /**
     * @deprecated Use static function create instead
     */
    @Deprecated
    public EventBuilder() {
    }

    public EventBuilder timestamp(long timestamp) {
        if (wasBuild) {
            throw new IllegalStateException("Builder already used");
        }
        this.timestamp = timestamp;
        return this;
    }

    public EventBuilder random(UUID random) {
        if (wasBuild) {
            throw new IllegalStateException("Builder already used");
        }
        this.random = random;
        return this;
    }

    public EventBuilder version(int version) {
        if (wasBuild) {
            throw new IllegalStateException("Builder already used");
        }
        this.version = version;
        return this;
    }

    public EventBuilder tag(String key, Variant value) {
        if (wasBuild) {
            throw new IllegalStateException("Builder already used");
        }
        this.containerBuilder.tag(key, value);
        return this;
    }

    public <T> EventBuilder tag(TagDescription<T> tag, Variant value) {
        if (wasBuild) {
            throw new IllegalStateException("Builder already used");
        }
        this.containerBuilder.tag(tag, value);
        return this;
    }

    public Event build() {
        if (wasBuild) {
            throw new IllegalStateException("Builder already used");
        } else {
            wasBuild = true;
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder encoder = new Encoder(stream);

        encoder.writeUnsignedByte(version);
        encoder.writeLong(timestamp);
        encoder.writeUuid(random);

        Container container = containerBuilder.build();
        CONTAINER_WRITER.write(encoder, container);

        return new Event(stream.toByteArray(), version, timestamp, random, container);
    }

    public static EventBuilder create() {
        return new EventBuilder();
    }

    public static EventBuilder create(final long timestamp, final UUID random) {
        return new EventBuilder()
                .version(1)
                .timestamp(timestamp)
                .random(random);
    }

    public static EventBuilder create(final long timestamp, final String uuidString) {
        return new EventBuilder()
                .version(1)
                .timestamp(timestamp)
                .random(UUID.fromString(uuidString));
    }
}
