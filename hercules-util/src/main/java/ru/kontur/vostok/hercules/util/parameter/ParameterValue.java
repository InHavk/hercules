package ru.kontur.vostok.hercules.util.parameter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.kontur.vostok.hercules.util.validation.ValidationResult;

/**
 * The value of {@link Parameter}
 *
 * @param <T> the value type
 * @author Gregory Koshelev
 */
public class ParameterValue<T> {
    private static final ParameterValue<?> EMPTY = new ParameterValue<>(null, ValidationResult.ok());
    private static final ParameterValue<?> MISSED = new ParameterValue<>(null, ValidationResult.missed());

    private final T value;
    private final ValidationResult result;

    private ParameterValue(T value, ValidationResult result) {
        this.value = value;
        this.result = result;
    }

    /**
     * Returns valid non null value. Otherwise throws exception.
     *
     * @return the value of type {@link T}
     * @throws IllegalStateException if value is empty
     * @throws IllegalStateException if value is invalid
     */
    @NotNull
    public T get() {
        if (isEmpty()) {
            throw new IllegalStateException("Is empty");
        }

        if (result.isOk()) {
            return value;
        }

        throw new IllegalStateException(result.error());
    }

    /**
     * Returns non null value if it exists or {@code other} if {@link ParameterValue#isEmpty()}. Otherwise throws exception.
     *
     * @param other the other value to return if {@link ParameterValue#isEmpty()}
     * @return the value of type {@link T}
     * @throws IllegalStateException if value is invalid
     */
    @Nullable
    public T orEmpty(@Nullable T other) {
        if (isEmpty()) {
            return other;
        }

        if (isOk()) {
            return value;
        }

        throw new IllegalStateException(result.error());
    }

    /**
     * Returns validation result.
     *
     * @return validation result
     */
    @NotNull
    public ValidationResult result() {
        return result;
    }

    /**
     * Returns {@code true} if value is valid.
     *
     * @return {@code true} if value is valid
     */
    public boolean isOk() {
        return result.isOk();
    }

    /**
     * Returns {@code true} if value is invalid.
     *
     * @return {@code true} if value is invalid
     */
    public boolean isError() {
        return result.isError();
    }

    /**
     * Returns {@code true} if value is empty and it is acceptable.
     *
     * @return {@code true} if value is empty and it is acceptable
     */
    public boolean isEmpty() {
        return this == EMPTY;
    }

    /**
     * Builds valid parameter's value.
     *
     * @param value the value
     * @param <T>   the value type
     * @return valid parameter's value
     */
    @NotNull
    public static <T> ParameterValue<T> of(@NotNull T value) {
        return new ParameterValue<>(value, ValidationResult.ok());
    }

    /**
     * Builds invalid parameter's value.
     *
     * @param result the validation result
     * @param <T>    the value type
     * @return invalid parameter's value
     */
    @NotNull
    public static <T> ParameterValue<T> invalid(@NotNull ValidationResult result) {
        return new ParameterValue<>(null, result);
    }

    /**
     * Builds invalid parameter's value.
     *
     * @param error the validation error
     * @param <T>   the value type
     * @return invalid parameter's value
     */
    @NotNull
    public static <T> ParameterValue<T> invalid(@Nullable String error) {
        return new ParameterValue<>(null, ValidationResult.error(error != null ? error : "unknown"));
    }

    /**
     * Returns valid empty parameter's value.
     *
     * @param <T> the value type
     * @return valid parameter's value
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public static <T> ParameterValue<T> empty() {
        return (ParameterValue<T>) EMPTY;
    }

    /**
     * Returns missed parameter's value.
     *
     * @param <T> the value type
     * @return invalid parameter's value
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public static <T> ParameterValue<T> missed() {
        return (ParameterValue<T>) MISSED;
    }
}
