package ru.kontur.vostok.hercules.protocol.encoder;

import ru.kontur.vostok.hercules.protocol.Container;
import ru.kontur.vostok.hercules.protocol.TinyString;
import ru.kontur.vostok.hercules.protocol.Variant;

import java.util.Map;

public class ContainerWriter implements Writer<Container> {

    public static final ContainerWriter INSTANCE = new ContainerWriter();

    private static final VariantWriter VARIANT_WRITER = VariantWriter.INSTANCE;

    @Override
    public void write(Encoder encoder, Container value) {
        encoder.writeContainerSize((short) value.count());
        for (Map.Entry<TinyString, Variant> entry : value.tags().entrySet()) {
            encoder.writeTinyString(entry.getKey());
            VARIANT_WRITER.write(encoder, entry.getValue());
        }
    }
}
