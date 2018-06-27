package ru.kontur.vostok.hercules.protocol.decoder;

public interface Reader<T> {

    T read(Decoder decoder);

    default void skip(Decoder decoder) {
        throw new UnsupportedOperationException();
    }
}
