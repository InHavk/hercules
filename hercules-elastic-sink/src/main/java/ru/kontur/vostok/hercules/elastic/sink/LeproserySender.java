package ru.kontur.vostok.hercules.elastic.sink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kontur.vostok.hercules.configuration.Scopes;
import ru.kontur.vostok.hercules.gate.client.GateClient;
import ru.kontur.vostok.hercules.gate.client.exception.BadRequestException;
import ru.kontur.vostok.hercules.gate.client.exception.UnavailableClusterException;
import ru.kontur.vostok.hercules.gate.client.util.EventWriterUtil;
import ru.kontur.vostok.hercules.health.MetricsCollector;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.protocol.Variant;
import ru.kontur.vostok.hercules.protocol.format.EventFormatter;
import ru.kontur.vostok.hercules.protocol.util.ContainerBuilder;
import ru.kontur.vostok.hercules.protocol.util.EventBuilder;
import ru.kontur.vostok.hercules.tags.CommonTags;
import ru.kontur.vostok.hercules.tags.ElasticSearchTags;
import ru.kontur.vostok.hercules.util.concurrent.Topology;
import ru.kontur.vostok.hercules.util.parameter.Parameter;
import ru.kontur.vostok.hercules.util.properties.PropertiesUtil;
import ru.kontur.vostok.hercules.util.time.TimeUtil;
import ru.kontur.vostok.hercules.util.validation.ValidationResult;
import ru.kontur.vostok.hercules.uuid.UuidGenerator;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author a.zhdanov
 */
class LeproserySender {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeproserySender.class);

    private static final String PROJECT_NAME = "hercules";
    private static final String SERVICE_NAME = "hercules-elastic-sink";

    private static final String COUNT_OF_EVENTS_SENDING_METER_KEY = "nonRetryableEventsSendingToGate";
    private static final String COUNT_OF_EVENTS_SENDING_WITH_ERROR_METER_KEY = "nonRetryableEventsSendingToGateWithError";

    private static final int EMPTY_LEPROSERY_EVENT_SIZE = 125;

    private final String leproseryStream;
    private final String leproseryIndex;
    private final GateClient gateClient;
    private final String leproseryApiKey;

    private final MetricsCollector metricsCollector;

    LeproserySender(Properties properties, MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;

        this.leproseryStream = PropertiesUtil.get(Props.LEPROSERY_STREAM, properties).get();
        this.leproseryIndex = PropertiesUtil.get(Props.LEPROSERY_INDEX, properties).get();
        this.leproseryApiKey = PropertiesUtil.get(Props.LEPROSERY_API_KEY, properties).get();

        Properties gateProperties = PropertiesUtil.ofScope(properties, Scopes.GATE_CLIENT);
        final String[] urls = PropertiesUtil.get(Props.URLS, gateProperties).get();
        Topology<String> whiteList = new Topology<>(urls);
        this.gateClient = new GateClient(gateProperties, whiteList);
    }

    /**
     * Convert event to leprosery like event and send to Leprosery.
     * @param eventErrorInfos  - map of event wrapper (contains event, event-index, event-id) -> result of validation with reason
     * @throws Exception - if any error
     */
    public void convertAndSend(Map<EventWrapper, ValidationResult> eventErrorInfos) throws Exception {
        send(convert(eventErrorInfos));
    }

    public void send(List<Event> events) throws Exception {
        int count = events.size();
        byte[] data = EventWriterUtil.toBytes(events.toArray(new Event[events.size()]));
        try {
            gateClient.send(leproseryApiKey, leproseryStream, data);

            LOGGER.info("Send to leprosery {} events", count);
            metricsCollector.meter(COUNT_OF_EVENTS_SENDING_METER_KEY).mark(count);
        } catch (BadRequestException | UnavailableClusterException e) {
            LOGGER.error("Leprosery sending error", e);
            metricsCollector.meter(COUNT_OF_EVENTS_SENDING_WITH_ERROR_METER_KEY).mark(count);
            throw e;
        }
    }

    private List<Event> convert(Map<EventWrapper, ValidationResult> eventErrorInfos) {
        return eventErrorInfos.entrySet().stream()
                .map(entry -> {
                    EventWrapper wrapper = entry.getKey();
                    ValidationResult validationResult = entry.getValue();
                    return toLeproseryEvent(wrapper.getEvent(), leproseryIndex, wrapper.getIndex(), validationResult.error());
                })
                .collect(Collectors.toList());
    }

    /**
     * Create Hercules Protocol event for leprosery with json line of invalid event
     *
     * @param event          non-retryable event
     * @param leproseryIndex name of destination index
     * @param index          to be used when put events to ELK
     * @param reason         message of error
     * @return Hercules Protocol event which is sent to leprosery
     */
    private Event toLeproseryEvent(Event event, String leproseryIndex, String index, String reason) {
        int minimalSize = EMPTY_LEPROSERY_EVENT_SIZE + Stream.of(PROJECT_NAME, SERVICE_NAME, reason, index, leproseryIndex)
                .mapToInt(value -> value.getBytes().length)
                .sum();
        String text = EventFormatter.format(event, false);
        boolean needCrop = (Constants.EXPECTED_EVENT_SIZE_BYTES - (minimalSize + text.getBytes().length)) < 0;
        if (needCrop) {
            text = new String(Arrays.copyOfRange(text.getBytes(), 0, Constants.EXPECTED_EVENT_SIZE_BYTES - minimalSize));
        }

        return EventBuilder.create()
                .version(1)
                .timestamp(TimeUtil.dateTimeToUnixTicks(ZonedDateTime.now()))
                .random(UuidGenerator.getClientInstance().next())
                .tag("message", Variant.ofString(reason))
                .tag(CommonTags.PROPERTIES_TAG, Variant.ofContainer(ContainerBuilder.create()
                        .tag(CommonTags.PROJECT_TAG, Variant.ofString(PROJECT_NAME))
                        .tag(CommonTags.SERVICE_TAG, Variant.ofString(SERVICE_NAME))
                        .tag("text", Variant.ofString(text))
                        .tag("original-index", Variant.ofString(index))
                        .tag(ElasticSearchTags.ELK_INDEX_TAG.getName(), Variant.ofString(leproseryIndex))
                        .build()))
                .build();
    }

    private static class Props {
        static final Parameter<String> LEPROSERY_STREAM = Parameter
                .stringParameter("stream")
                .required()
                .build();

        static final Parameter<String> LEPROSERY_API_KEY = Parameter
                .stringParameter("apiKey")
                .required()
                .build();

        static final Parameter<String[]> URLS = Parameter
                .stringArrayParameter("urls")
                .required()
                .build();

        static final Parameter<String> LEPROSERY_INDEX = Parameter
                .stringParameter("index")
                .withDefault("leprosery")
                .build();

    }

}
