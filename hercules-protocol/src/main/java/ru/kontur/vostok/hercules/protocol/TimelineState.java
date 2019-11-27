package ru.kontur.vostok.hercules.protocol;

/**
 * Timeline state is used to determine the last read position of Timeline       <br>
 * Note: Timeline consists of several slices. Thus, Timeline state is presented by multiple Slice states.
 *
 * @author Gregory Koshelev
 */
public class TimelineState {
    private static final int SIZE_OF_SLICE_COUNT = 4;
    /**
     * State for each slice
     */
    private final TimelineSliceState[] sliceStates;

    /**
     * Create immutable Timeline state
     *
     * @param sliceStates is states for each Timeline's Slice
     */
    public TimelineState(TimelineSliceState[] sliceStates) {
        this.sliceStates = sliceStates;
    }

    public int getSliceCount() {
        return sliceStates.length;
    }

    public TimelineSliceState[] getSliceStates() {
        return sliceStates;
    }

    public int sizeOf() {
        return SIZE_OF_SLICE_COUNT + TimelineSliceState.fixedSizeOf() * getSliceCount();
    }
}
