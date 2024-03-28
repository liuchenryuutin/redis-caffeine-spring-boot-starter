package lc.cy.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lc.cy.cache.CaffineChangeMessageListener;
import lc.cy.cache.caffine.RedisCaffeineCacheManager;
import lc.cy.cache.caffine.RedisCaffeineTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@ConditionalOnClass({RedisOperations.class, Caffeine.class})
@ConditionalOnProperty(value = "spring.cache.redis-caffeine.enable", havingValue = "true")
@EnableConfigurationProperties(CacheRedisCaffeineProperties.class)
public class CacheRedisCaffeineAutoConfig {

    private CacheRedisCaffeineProperties cacheRedisCaffeineProperties;

    private RedisTemplate<Object, Object> redisTemplate;

    public CacheRedisCaffeineAutoConfig(CacheRedisCaffeineProperties cacheRedisCaffeineProperties, RedisTemplate<Object, Object> redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("RedisTemplate is not exists.");
        }
        this.cacheRedisCaffeineProperties = cacheRedisCaffeineProperties;
        this.redisTemplate = redisTemplate;
    }

    @Primary
    @Bean(name = "redisCaffeineCacheManager")
    public RedisCaffeineCacheManager cacheManager() {
        return new RedisCaffeineCacheManager(cacheRedisCaffeineProperties, redisTemplate);
    }

    @Bean(name = "redisCaffeineMessageChangeListenser")
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisCaffeineCacheManager redisCaffeineCacheManager) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(), 20l, TimeUnit.SECONDS
                , new LinkedBlockingQueue<>(100000), new DefaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setTaskExecutor(threadPoolExecutor);
        redisMessageListenerContainer.setConnectionFactory(redisTemplate.getConnectionFactory());
        CaffineChangeMessageListener cacheMessageListener = new CaffineChangeMessageListener(redisTemplate, redisCaffeineCacheManager);
        redisMessageListenerContainer.addMessageListener(cacheMessageListener,
                new ChannelTopic(cacheRedisCaffeineProperties.getRedis().getTopic()));
        return redisMessageListenerContainer;
    }

    @Bean
    public RedisCaffeineTemplate redisCaffeineTemplate(RedisCaffeineCacheManager redisCaffeineCacheManager) {
        return new RedisCaffeineTemplate(redisCaffeineCacheManager);
    }

    private static class DefaultThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "redis-caffeine-listener-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}