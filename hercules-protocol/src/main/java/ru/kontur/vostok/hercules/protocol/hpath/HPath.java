package ru.kontur.vostok.hercules.protocol.hpath;

import ru.kontur.vostok.hercules.protocol.Container;
import ru.kontur.vostok.hercules.protocol.TinyString;
import ru.kontur.vostok.hercules.protocol.Type;
import ru.kontur.vostok.hercules.protocol.Variant;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Gregory Koshelev
 */
public class HPath {
    private final String path;

    private final TinyString[] tags;

    private HPath(String path, TinyString[] tags) {
        this.path = path;
        this.tags = tags;
    }

    public Variant extract(Container container) {
        if (tags.length == 0) {
            return null;
        }

        int size = tags.length;
        Container current = container;

        for (int i = 0; i < size - 1; i++) {
            TinyString tag = tags[i];
            Variant tagValue = current.get(tag);
            if (tagValue == null || tagValue.getType() != Type.CONTAINER) {
                return null;
            }
            current = (Container) tagValue.getValue();
        }

        return current.get(tags[size - 1]);
    }

    public TinyString getRootTag() {
        return (tags.length > 0) ? tags[0] : null;
    }

    public HPath getSubHPath() {
        if (tags.length == 0) {
            return this;
        }

        String[] subTags = new String[tags.length - 1];
        System.arraycopy(tags, 1, subTags, 0, tags.length - 1);

        return fromTags(subTags);
    }

    public String getPath() {
        return path;
    }

    public TagIterator it() {
        return new TagIterator();
    }

    private static TinyString[] pathToTags(String path) {
        if (path == null) {
            return new TinyString[0];
        }

        return TinyString.toTinyStrings(path.split("/"));
    }

    private static String tagsToPath(String... tags) {
        return String.join("/", tags);
    }

    public static HPath fromTag(String tag) {
        return new HPath(tag, new TinyString[]{TinyString.of(tag)});
    }

    public static HPath fromTags(String... tags) {
        return new HPath(tagsToPath(tags), TinyString.toTinyStrings(tags));
    }

    public static HPath fromPath(String path) {
        return new HPath(path, pathToTags(path));
    }

    /**
     * Iterate over tags in HPath.
     * <p>
     * It is not thread-safe.
     */
    public class TagIterator implements Iterator<TinyString> {
        private int cursor = 0;

        @Override
        public boolean hasNext() {
            return cursor < tags.length;
        }

        @Override
        public TinyString next() {
            int i = cursor;
            if (i >= tags.length) {
                throw new NoSuchElementException();
            }
            cursor = i + 1;
            return tags[i];
        }
    }
}
