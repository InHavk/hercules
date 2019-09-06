package ru.kontur.vostok.hercules.sentry.sink.converters;

import io.sentry.event.EventBuilder;
import io.sentry.event.Sdk;
import io.sentry.event.User;
import io.sentry.event.UserBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.SentryStackTraceElement;
import io.sentry.event.interfaces.UserInterface;
import ru.kontur.vostok.hercules.protocol.Container;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.protocol.Type;
import ru.kontur.vostok.hercules.protocol.Variant;
import ru.kontur.vostok.hercules.protocol.Vector;
import ru.kontur.vostok.hercules.tags.CommonTags;
import ru.kontur.vostok.hercules.tags.ExceptionTags;
import ru.kontur.vostok.hercules.tags.LogEventTags;
import ru.kontur.vostok.hercules.protocol.util.ContainerUtil;
import ru.kontur.vostok.hercules.protocol.util.TagDescription;
import ru.kontur.vostok.hercules.tags.SentryTags;
import ru.kontur.vostok.hercules.tags.UserTags;
import ru.kontur.vostok.hercules.util.Lazy;
import ru.kontur.vostok.hercules.util.application.ApplicationContextHolder;
import ru.kontur.vostok.hercules.util.time.TimeUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Convert Hercules event to Sentry event builder
 */
public class SentryEventConverter {

    private static final Lazy<Sdk> SDK = new Lazy<>(() -> new Sdk(
            "hercules-sentry-sink",
            ApplicationContextHolder.get().getVersion(),
            null
    ));

    private static final Set<String> STANDARD_PROPERTIES = Stream.of(
            CommonTags.ENVIRONMENT_TAG,
            SentryTags.RELEASE_TAG,
            SentryTags.TRACE_ID_TAG,
            SentryTags.FINGERPRINT_TAG,
            SentryTags.PLATFORM_TAG,
            SentryTags.LOGGER_TAG)
            .map(TagDescription::getName).collect(Collectors.toSet());

    private static final Set<String> STANDARD_CONTEXTS = Stream.of(
            "os",
            "browser",
            "runtime",
            "device",
            "app",
            "gpu")
            .collect(Collectors.toSet());

    private static final String HIDING_SERVER_NAME = " ";
    private static final String DEFAULT_PLATFORM = "";
    private static final String DELIMITER = ".";

    public static io.sentry.event.Event convert(Event logEvent) {

        EventBuilder eventBuilder = new EventBuilder(logEvent.getUuid());

        eventBuilder.withTimestamp(Date.from(TimeUtil.unixTicksToInstant(logEvent.getTimestamp())));

        eventBuilder.withServerName(HIDING_SERVER_NAME);

        final Container payload = logEvent.getPayload();
        ContainerUtil.extract(payload, LogEventTags.MESSAGE_TAG)
                .ifPresent(eventBuilder::withMessage);

        ContainerUtil.extract(payload, LogEventTags.LEVEL_TAG)
                .flatMap(SentryLevelEnumParser::parse)
                .ifPresent(eventBuilder::withLevel);

        ContainerUtil.extract(payload, LogEventTags.EXCEPTION_TAG)
                .ifPresent(exception -> {
                    final ExceptionInterface exceptionInterface = convertException(exception);
                    eventBuilder.withSentryInterface(exceptionInterface);
                    eventBuilder.withPlatform(SentryEventConverter.extractPlatform(exceptionInterface));
                });

        ContainerUtil.extract(payload, LogEventTags.STACK_TRACE_TAG)
                .ifPresent(stackTrace -> eventBuilder.withExtra("stackTrace", stackTrace));

        ContainerUtil.extract(payload, CommonTags.PROPERTIES_TAG).ifPresent(properties -> {

            ContainerUtil.extract(properties, CommonTags.ENVIRONMENT_TAG)
                    .ifPresent(eventBuilder::withEnvironment);

            ContainerUtil.extract(properties, SentryTags.RELEASE_TAG)
                    .ifPresent(eventBuilder::withRelease);

            ContainerUtil.extract(properties, SentryTags.TRACE_ID_TAG)
                    .ifPresent(eventBuilder::withTransaction);

            ContainerUtil.extract(properties, SentryTags.FINGERPRINT_TAG)
                    .ifPresent(eventBuilder::withFingerprint);

            ContainerUtil.extract(properties, SentryTags.PLATFORM_TAG)
                    .map(String::toLowerCase)
                    .filter(PLATFORMS::contains)
                    .ifPresent(eventBuilder::withPlatform);

            ContainerUtil.extract(properties, SentryTags.LOGGER_TAG)
                    .ifPresent(eventBuilder::withLogger);

            writeOtherTags(properties, eventBuilder);
        });

        io.sentry.event.Event sentryEvent = eventBuilder.build();
        sentryEvent.setSdk(SDK.get());

        return sentryEvent;
    }

    private static void writeOtherTags(final Container properties, EventBuilder eventBuilder) {
        UserBuilder userBuilder = new UserBuilder();
        Map<String, Object> otherUserDataMap = new HashMap<>();
        Map<String, Map<String, Object>> contexts = new HashMap<>();

        for (Map.Entry<String, Variant> tagValuePair : properties) {
            final String tagName = tagValuePair.getKey();
            final Variant value = tagValuePair.getValue();

            if (STANDARD_PROPERTIES.contains(tagName)) {
                continue;
            }

            Optional<String> userFieldOptional = cutOffPrefixIfExists("user", tagName);
            if (userFieldOptional.isPresent()) {
                String userField = userFieldOptional.get();
                if (userField.equals(UserTags.ID_TAG.getName()) && value.getType() == Type.STRING) {
                    userBuilder.setId(extractString(value));
                } else if (userField.equals(UserTags.IP_ADDRESS_TAG.getName()) && value.getType() == Type.STRING) {
                    userBuilder.setIpAddress(extractString(value));
                } else if (userField.equals(UserTags.USERNAME_TAG.getName()) && value.getType() == Type.STRING) {
                    userBuilder.setUsername(extractString(value));
                } else if (userField.equals(UserTags.EMAIL_TAG.getName()) && value.getType() == Type.STRING) {
                    userBuilder.setEmail(extractString(value));
                } else {
                    otherUserDataMap.put(userField, extract(value));
                }
                continue;
            }

            boolean valueIsContext = false;
            for (String contextName : STANDARD_CONTEXTS) {
                Optional<String> contextFieldOptional = cutOffPrefixIfExists(contextName, tagName);
                if (contextFieldOptional.isPresent()) {
                    String contextField = contextFieldOptional.get();
                    if (!contexts.containsKey(contextName)) {
                        contexts.put(contextName, new HashMap<>());
                    }
                    contexts.get(contextName).put(contextField, extract(value));
                    valueIsContext = true;
                    break;
                }
            }
            if (valueIsContext) {
                continue;
            }

            if (value.getType() == Type.STRING) {
                eventBuilder.withTag(tagName, extractString(value));
                continue;
            }

            eventBuilder.withExtra(tagName, extract(value));
        }

        User user = userBuilder.setData(otherUserDataMap).build();
        eventBuilder.withSentryInterface(new UserInterface(
                user.getId(),
                user.getUsername(),
                user.getIpAddress(),
                user.getEmail(),
                user.getData()));
        eventBuilder.withContexts(contexts);
    }


    private static Optional<String> cutOffPrefixIfExists(String prefix, String sourceName) {
        final String prefixWithDelimiter = prefix + DELIMITER;
        if (sourceName.length() <= prefixWithDelimiter.length()) {
            return Optional.empty();
        }
        if (!sourceName.substring(0, prefixWithDelimiter.length()).equals(prefixWithDelimiter)) {
            return Optional.empty();
        }
        return Optional.of(sourceName.substring(prefixWithDelimiter.length()));
    }

    private static String extractString(Variant variant) {
        if (variant.getType() == Type.STRING) {
            return new String((byte[]) variant.getValue(), StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static Object extract(Variant variant) {
        switch (variant.getType()) {
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLAG:
            case FLOAT:
            case DOUBLE:
            case UUID:
                return variant.getValue();
            case STRING:
                return new String((byte[]) variant.getValue(), StandardCharsets.UTF_8);
            case CONTAINER:
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, Variant> entry : (Container) variant.getValue()) {
                    map.put(entry.getKey(), extract(entry.getValue()));
                }
                return map;
            case VECTOR:
                Vector vector = (Vector) variant.getValue();
                Object[] objects = (Object[]) vector.getValue();
                List<Object> resultList = new ArrayList<>();
                for (Object object : objects) {
                    resultList.add(extract(new Variant(vector.getType(), object)));
                }
                return resultList;
            default:
                return "null";
        }
    }

    private static ExceptionInterface convertException(final Container exception) {

        LinkedList<SentryException> sentryExceptions = new LinkedList<>();
        convertException(exception, sentryExceptions);
        return new ExceptionInterface(sentryExceptions);
    }

    private static void convertException(final Container currentException, final LinkedList<SentryException> converted) {
        converted.add(SentryExceptionConverter.convert(currentException));
        ContainerUtil.extract(currentException, ExceptionTags.INNER_EXCEPTIONS_TAG)
                .ifPresent(exceptions -> Arrays.stream(exceptions).forEach(exception -> convertException(exception, converted)));
    }

    private static String extractPlatform(final ExceptionInterface exceptionInterface) {
        return exceptionInterface.getExceptions().stream()
                .flatMap(e -> Arrays.stream(e.getStackTraceInterface().getStackTrace()))
                .map(SentryStackTraceElement::getFileName)
                .map(SentryEventConverter::resolvePlatformByFileName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(DEFAULT_PLATFORM);
    }

    private static Optional<String> resolvePlatformByFileName(final String fileName) {
        if (Objects.isNull(fileName)) {
            return Optional.empty();
        }

        final String lowerCaseFileName = fileName.toLowerCase();

        if (lowerCaseFileName.endsWith(".java")) {
            return Optional.of("java");
        } else if (lowerCaseFileName.endsWith(".cs")) {
            return Optional.of("csharp");
        } else if (lowerCaseFileName.endsWith(".py")) {
            return Optional.of("python");
        } else {
            return Optional.empty();
        }
    }

    private static final Set<String> PLATFORMS = new HashSet<>(Arrays.asList(
            "as3",
            "c",
            "cfml",
            "cocoa",
            "csharp",
            "go",
            "java",
            "javascript",
            "native",
            "node",
            "objc",
            "other",
            "perl",
            "php",
            "python",
            "ruby"
    ));
}
