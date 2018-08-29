package ru.kontur.vostok.hercules.gate;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import ru.kontur.vostok.hercules.auth.AuthManager;
import ru.kontur.vostok.hercules.configuration.util.PropertiesUtil;
import ru.kontur.vostok.hercules.meta.stream.StreamRepository;
import ru.kontur.vostok.hercules.metrics.MetricsCollector;
import ru.kontur.vostok.hercules.util.properties.PropertiesExtractor;

import java.util.Properties;

/**
 * @author Gregory Koshelev
 */
public class HttpServer {
    private final Undertow undertow;

    public HttpServer(MetricsCollector metricsCollector, Properties properties, AuthManager authManager, AuthValidationManager authValidationManager, EventSender eventSender, StreamRepository streamRepository) {
        String host = properties.getProperty("host", "0.0.0.0");
        int port = PropertiesExtractor.get(properties, "port", 6306);

        Properties throttlingProperties = PropertiesUtil.ofScope(properties, "throttling");

        SendRequestProcessor sendRequestProcessor = new SendRequestProcessor(metricsCollector, eventSender);

        HttpHandler sendAsyncHandler = new GateHandler(metricsCollector, authManager, sendRequestProcessor, authValidationManager, streamRepository, true);
        HttpHandler sendHandler = new GateHandler(metricsCollector, authManager, sendRequestProcessor, authValidationManager, streamRepository, false);

        HttpHandler handler = Handlers.routing()
                .get("/ping", exchange -> {
                    exchange.setStatusCode(200);
                    exchange.endExchange();
                })
                .post("/stream/sendAsync", sendAsyncHandler)
                .post("/stream/send", sendHandler);

        undertow = Undertow
                .builder()
                .addHttpListener(port, host)
                .setHandler(handler)
                .build();
    }

    public void start() {
        undertow.start();
    }

    public void stop() {
        undertow.stop();
    }
}
