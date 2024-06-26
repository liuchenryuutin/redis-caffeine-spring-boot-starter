package lc.cy.cache;

import lc.cy.cache.caffine.CacheMessage;
import lc.cy.cache.caffine.RedisCaffeineCacheManager;
import lc.cy.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

public class CaffineChangeMessageListener implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(CaffineChangeMessageListener.class);

    private RedisTemplate<Object, Object> redisTemplate;

    private RedisCaffeineCacheManager redisCaffeineCacheManager;

    public CaffineChangeMessageListener(RedisTemplate<Object, Object> redisTemplate,
                                        RedisCaffeineCacheManager redisCaffeineCacheManager) {
        super();
        this.redisTemplate = redisTemplate;
        this.redisCaffeineCacheManager = redisCaffeineCacheManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Object msg = redisTemplate.getValueSerializer().deserialize(message.getBody());
        if(msg == null) {
            return;
        }
        CacheMessage cacheMessage = JsonUtil.fromJson(msg.toString(), CacheMessage.class);
        logger.debug("recevice a redis topic message, clear local cache, the cacheName is {}, the key is {}",
                cacheMessage.getCacheName(), cacheMessage.getKey());
        if(cacheMessage == null) {
            return;
        }
        redisCaffeineCacheManager.clearLocal(cacheMessage.getCacheName(), cacheMessage.getKey());
    }

}

