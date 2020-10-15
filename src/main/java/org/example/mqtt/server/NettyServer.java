package org.example.mqtt.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.example.mqtt.server.config.MqttServerProperties;
import org.example.mqtt.server.handlers.BrokerHandler;
import org.example.mqtt.server.handlers.MqttLoggerHandler;
import org.example.mqtt.server.handlers.MqttWebSocketCodec;
import org.example.mqtt.utils.RemotingUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 罗涛
 * @title NettyServer
 * @date 2020/6/22 15:15
 */
@Slf4j
@Component
public class NettyServer implements InitializingBean, SmartLifecycle {
    @Autowired
    private MqttLoggerHandler mqttLoggerHandler;

    @Autowired
    BrokerHandler brokerHandler;

    @Autowired
    MqttServerProperties mqttServerProperties;

    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Map<Integer, Channel> channelMap = new ConcurrentHashMap<>();
    private SslContext sslContext;
    private boolean running = false;
    private boolean sslEnable = false;

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Override
    public void start() {
        initEventPool();
        sslEnable = mqttServerProperties.getSslEnable();
        sslContext = buildSslContext();
        mqttServer();
        webSocketServer();
        running = true;
    }

    private SslContext buildSslContext() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(mqttServerProperties.getServerKeyPath());
            keyStore.load(inputStream, mqttServerProperties.getSslPassword().toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, mqttServerProperties.getSslPassword().toCharArray());
            return SslContextBuilder.forServer(kmf).build();

            /*
             * 加载server.keystore
             * JKS -> PKCS12
             */
//            KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
//            InputStream serverKeyStoreInputStream = this.getClass().getClassLoader().getResourceAsStream(mqttProperties.getServerKeyPath());
//            serverKeyStore.load(serverKeyStoreInputStream, mqttProperties.getSslPassword().toCharArray());
//            /*
//             * 加载servertrust.keystore
//             *
//             */
//            KeyStore serverTrustKeyStore = KeyStore.getInstance("PKCS12");
//            InputStream serverTrustKeyStoreInputStream = this.getClass().getClassLoader().getResourceAsStream(mqttProperties.getRootKeyPath());
//            serverTrustKeyStore.load(serverTrustKeyStoreInputStream, mqttProperties.getSslPassword().toCharArray());
//
//            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
//            kmf.init(serverKeyStore, mqttProperties.getSslPassword().toCharArray());
//
//            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//            tmf.init(serverTrustKeyStore);
//
//            return SslContextBuilder.forServer(kmf).trustManager(tmf).build();
        } catch (Exception e) {
            log.info("初始化sslContext发异常：" + e.getMessage(), e);
            return null;
        }
    }

    private void mqttServer() {
        try {
            ServerBootstrap server = new ServerBootstrap();
            server.group(boss, workers)
                .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        if (mqttServerProperties.getSslEnable()) {
                            // Netty提供的SSL处理
                            SSLEngine sslEngine = sslContext.newEngine(socketChannel.alloc());
                            // 服务端模式
                            sslEngine.setUseClientMode(false);
                            // 不需要验证客户端
                            sslEngine.setNeedClientAuth(false);
                            pipeline.addLast("ssl", new SslHandler(sslEngine));
                        }
                        pipeline.addLast("mqtt-decoder", new MqttDecoder());
                        pipeline.addLast("mqtt-encoder", MqttEncoder.INSTANCE);
                        pipeline.addLast("logger", mqttLoggerHandler);
                        pipeline.addLast("broker", brokerHandler);
                    }
                });
            int port = sslEnable? mqttServerProperties.getSslPort(): mqttServerProperties.getTcpPort();
            ChannelFuture channelFuture = server.bind(port).sync();
            log.info("成功监听MQTT端口：{}, SSL加密：{}", port, sslEnable);
            Channel channel = channelFuture.channel();
            channelMap.put(port, channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void webSocketServer(){
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, workers)
                    .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 500)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            if (mqttServerProperties.getSslEnable()) {
                                // Netty提供的SSL处理
                                SSLEngine sslEngine = sslContext.newEngine(socketChannel.alloc());
                                // 服务端模式
                                sslEngine.setUseClientMode(false);
                                // 不需要验证客户端
                                sslEngine.setNeedClientAuth(false);
                                pipeline.addLast("ssl", new SslHandler(sslEngine));
                            }
                            // 将请求和应答消息编码或解码为HTTP消息
                            pipeline.addLast("http-codec", new HttpServerCodec());
                            // 将HTTP消息的多个部分合成一条完整的HTTP消息
                            pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
                            // 将HTTP消息进行压缩编码
                            pipeline.addLast("compressor ", new HttpContentCompressor());
                            pipeline.addLast("protocol", new WebSocketServerProtocolHandler(mqttServerProperties.getWebsocketPath(), "mqtt,mqttv3.1,mqttv3.1.1", true, 65536));
                            pipeline.addLast("mqttWebSocket", new MqttWebSocketCodec());
                            pipeline.addLast("decoder", new MqttDecoder());
                            pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                            pipeline.addLast("logger", mqttLoggerHandler);
                            pipeline.addLast("broker", brokerHandler);
                        }
                    });
            int wsPort = sslEnable? mqttServerProperties.getWssPort(): mqttServerProperties.getWsPort();
            Channel wsChannel = bootstrap.bind(wsPort).sync().channel();
            log.info("成功监听WebSocket端口：{}, SSL加密：{}", wsPort, sslEnable);
            channelMap.put(wsPort, wsChannel);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        try {
            log.warn("程序退出，Netty执行stop方法，优雅关闭所有连接...");
            for (Channel channel : channelMap.values()) {
                channel.close().syncUninterruptibly();
            }
            boss.shutdownGracefully();
            workers.shutdownGracefully();
            boss.awaitTermination(30, TimeUnit.SECONDS);
            workers.awaitTermination(30, TimeUnit.SECONDS);
            running = false;
        } catch (Exception e) {
            log.error("优雅关闭发生异常：" + e.getMessage(), e);
        }
    }

    /**
     * 初始化EventPool 参数
     */
    private void initEventPool() {
        int testBossThreadNum = 1;
        int testWorkerThreadNum = Runtime.getRuntime().availableProcessors() * 2;
        if (useEpoll()) {
            boss = new EpollEventLoopGroup(testBossThreadNum, new ThreadFactory() {
                private AtomicInteger index = new AtomicInteger(0);

                public Thread newThread(Runnable r) {
                    return new Thread(r, "LINUX_BOSS_" + index.incrementAndGet());
                }
            });
            workers = new EpollEventLoopGroup(testWorkerThreadNum, new ThreadFactory() {
                private AtomicInteger index = new AtomicInteger(0);

                public Thread newThread(Runnable r) {
                    return new Thread(r, "LINUX_WORK_" + index.incrementAndGet());
                }
            });
        } else {
            boss = new NioEventLoopGroup(testBossThreadNum, new ThreadFactory() {
                private AtomicInteger index = new AtomicInteger(0);

                public Thread newThread(Runnable r) {
                    return new Thread(r, "BOSS_" + index.incrementAndGet());
                }
            });
            workers = new NioEventLoopGroup(testWorkerThreadNum, new ThreadFactory() {
                private AtomicInteger index = new AtomicInteger(0);

                public Thread newThread(Runnable r) {
                    return new Thread(r, "WORK_" + index.incrementAndGet());
                }
            });
        }
    }

    private boolean useEpoll() {
        return RemotingUtil.isLinuxPlatform() && Epoll.isAvailable();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
