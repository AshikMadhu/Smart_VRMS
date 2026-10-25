package in.smartvrms.server.api;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import javax.net.ssl.SSLContext;

/** Configuration for the local JDK HTTP/HTTPS server. Bound strictly to 127.0.0.1. */
public final class HttpServerConfig {
    private final InetSocketAddress bindAddress;
    private final SSLContext sslContext;

    public HttpServerConfig(int port) {
        this(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), null);
    }

    public HttpServerConfig(InetSocketAddress bindAddress, SSLContext sslContext) {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.sslContext = sslContext;
    }

    public InetSocketAddress bindAddress() { return bindAddress; }
    public SSLContext sslContext() { return sslContext; }
    public boolean isTls() { return sslContext != null; }
}
