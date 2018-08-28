package ru.kontur.vostok.hercules.http.jetty;

import ru.kontur.vostok.hercules.http.HttpHeaders;
import ru.kontur.vostok.hercules.http.HttpMethod;
import ru.kontur.vostok.hercules.http.HttpServerRequest;
import ru.kontur.vostok.hercules.http.HttpServerRequestException;
import ru.kontur.vostok.hercules.http.HttpServerResponse;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gregory Koshelev
 */
public class JettyHttpServerRequest implements HttpServerRequest {
    private final AsyncContext asyncContext;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final HttpServerResponse httpServerResponse;

    JettyHttpServerRequest(AsyncContext asyncContext) {
        this.asyncContext = asyncContext;
        this.request = (HttpServletRequest) asyncContext.getRequest();
        this.response = (HttpServletResponse) asyncContext.getResponse();
        this.httpServerResponse = new JettyHttpServerResponse(response);
    }

    @Override
    public HttpMethod getMethod() {
        return HttpMethod.valueOf(request.getMethod());
    }

    @Override
    public String getPath() {
        return request.getPathInfo();
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public byte[] readBody() throws HttpServerRequestException {
        try {
            InputStream body = request.getInputStream();
            String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
            if (contentLength != null) {
                int length = Integer.parseInt(contentLength);
                byte[] data = new byte[length];
                int actual = body.read(data);
                if (actual != length) {
                    throw new HttpServerRequestException("Expect " + length + " bytes but read only " + actual + " bytes");
                }
                return data;
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int count = 0;
                byte[] buffer = new byte[1024];
                while ((count = body.read(buffer)) > 0) {
                    baos.write(buffer, 0, count);
                }
                return baos.toByteArray();
            }
        } catch (IOException exception) {
            throw new HttpServerRequestException(exception);
        }
    }

    @Override
    public HttpServerResponse getResponse() {
        return httpServerResponse;
    }

    @Override
    public void complete() {
        asyncContext.complete();
    }
}
