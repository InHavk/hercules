package ru.kontur.vostok.hercules.util.validation;

import java.util.Optional;

/**
 * Validator - common interface for validator
 *
 * @author Kirill Sulim
 */
@FunctionalInterface
@Deprecated
public interface OldValidator<T> {

    /**
     * Validate value of type t
     *
     * @param value value to validate
     * @return empty or validation error description
     */
    Optional<String> validate(T value);
}
