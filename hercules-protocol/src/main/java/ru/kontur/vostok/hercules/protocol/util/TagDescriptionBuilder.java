package ru.kontur.vostok.hercules.protocol.util;

import ru.kontur.vostok.hercules.protocol.Container;
import ru.kontur.vostok.hercules.protocol.TinyString;
import ru.kontur.vostok.hercules.protocol.Type;
import ru.kontur.vostok.hercules.protocol.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * TagDescriptionBuilder
 *
 * @author Kirill Sulim
 */
public class TagDescriptionBuilder<T> {

    private final String tagName;
    private final Map<Type, Function<Object, ? extends T>> scalarExtractors = new HashMap<>();
    private final Map<Type, Function<Object, ? extends T>> vectorExtractors = new HashMap<>();

    public TagDescriptionBuilder(String tagName) {
        this.tagName = tagName;
    }

    public static <T> TagDescriptionBuilder<T> tag(String name, Class<T> clazz) {
        return new TagDescriptionBuilder<>(name);
    }

    public static TagDescriptionBuilder<String> string(String name) {
        return new TagDescriptionBuilder<String>(name)
                .addScalarExtractor(Type.STRING, StandardExtractors::extractString);
    }

    public static TagDescriptionBuilder<String[]> stringVector(String name) {
        return new TagDescriptionBuilder<String[]>(name)
                .addVectorExtractor(Type.STRING, StandardExtractors::extractStringArray);
    }

    public static TagDescriptionBuilder<Container[]> containerVector(String name) {
        return new TagDescriptionBuilder<Container[]>(name)
                .addVectorExtractor(Type.CONTAINER, StandardExtractors::extractContainerArray);
    }

    public static TagDescriptionBuilder<Container> container(final String name) {
        return new TagDescriptionBuilder<Container>(name)
                .addScalarExtractor(Type.CONTAINER, StandardExtractors::extractContainer);
    }

    public static <T> TagDescriptionBuilder<T> parsable(String name, Function<String, ? extends T> parser) {
        return string(name).convert(parser);
    }

    public static <T extends Enum<T>> TagDescriptionBuilder<T> enumValue(String name, Class<T> clazz) {
        return parsable(name, s -> Enum.valueOf(clazz, s.toUpperCase()));
    }

    public static TagDescriptionBuilder<Short> shortTag(final String name) {
        return new TagDescriptionBuilder<Short>(name)
            .addScalarExtractor(Type.BYTE, o -> ((Byte) o).shortValue())
            .addScalarExtractor(Type.SHORT, o -> (Short) o);
    }

    public static TagDescriptionBuilder<Integer> integer(String name) {
        return new TagDescriptionBuilder<Integer>(name)
                .addScalarExtractor(Type.BYTE, o -> ((Byte) o).intValue())
                .addScalarExtractor(Type.SHORT, o -> ((Short) o).intValue())
                .addScalarExtractor(Type.INTEGER, o -> (Integer) o);
    }

    public static TagDescriptionBuilder<Long> longTag(final String name) {
        return new TagDescriptionBuilder<Long>(name)
            .addScalarExtractor(Type.BYTE, o -> ((Byte) o).longValue())
            .addScalarExtractor(Type.SHORT, o -> ((Short) o).longValue())
            .addScalarExtractor(Type.INTEGER, o -> ((Integer) o).longValue())
            .addScalarExtractor(Type.LONG, o -> (Long) o);
    }

    public static TagDescriptionBuilder<UUID> uuid(final String name) {
        return new TagDescriptionBuilder<UUID>(name)
            .addScalarExtractor(Type.UUID, o -> (UUID) o);
    }

    public TagDescriptionBuilder<T> addScalarExtractor(Type type, Function<Object, ? extends T> extractor) {
        this.scalarExtractors.put(type, extractor);
        return this;
    }

    public TagDescriptionBuilder<T> addVectorExtractor(Type type, Function<Object, ? extends T> extractor) {
        this.vectorExtractors.put(type, extractor);
        return this;
    }

    public TagDescriptionBuilder<T> addDefault(Supplier<? extends T> supplier) {
        this.scalarExtractors.put(null, ignore -> supplier.get());
        return this;
    }

    public <T2> TagDescriptionBuilder<T2> convert(Function<? super T, ? extends T2> converter) {
        TagDescriptionBuilder<T2> result = new TagDescriptionBuilder<>(this.tagName);
        this.scalarExtractors.forEach((type, extractor) -> result.addScalarExtractor(type, extractor.andThen(converter)));//TODO: Fix me!
        this.vectorExtractors.forEach((type, extractor) -> result.addVectorExtractor(type, extractor.andThen(converter)));
        return result;
    }

    public TagDescriptionBuilder<Optional<T>> optional() {
        return this.convert(Optional::of)
                .addDefault(Optional::empty);
    }

    public TagDescription<T> build() {
        Map<Type, Function<Object, ? extends T>> extractors = new HashMap<>(scalarExtractors);
        if (!vectorExtractors.isEmpty()) {
            extractors.put(Type.VECTOR, (o) -> {
                Vector v = (Vector) o;
                return vectorExtractors.getOrDefault(v.getType(), (o1) -> {
                    throw new IllegalArgumentException("Unsupported type in Vector: " + v.getType());
                }).apply(v.getValue());
            });
        }
        return new TagDescription<>(TinyString.of(tagName), extractors);
    }
}
