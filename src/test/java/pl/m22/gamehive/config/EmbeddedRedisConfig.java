package pl.m22.gamehive.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

@Configuration
@Profile("test")
public class EmbeddedRedisConfig {

    private static final int REDIS_PORT = 16379;
    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        if (isPortAvailable(REDIS_PORT)) {
            redisServer = new RedisServer(REDIS_PORT);
            redisServer.start();
        }
    }

    @PreDestroy
    public void stopRedis() throws IOException {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
