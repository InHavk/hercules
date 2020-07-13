package ru.kontur.vostok.hercules.elastic.sink.mapping;

import ru.kontur.vostok.hercules.elastic.sink.StackTraceCreator;
import ru.kontur.vostok.hercules.json.transformer.Transformer;
import ru.kontur.vostok.hercules.protocol.Container;
import ru.kontur.vostok.hercules.protocol.Variant;

/**
 * @author Gregory Koshelev
 */
public class ExceptionToStackTraceTransformer implements Transformer {
    @Override
    public String transform(Variant value) {
        return StackTraceCreator.createStackTrace((Container)value.getValue());
    }
}
