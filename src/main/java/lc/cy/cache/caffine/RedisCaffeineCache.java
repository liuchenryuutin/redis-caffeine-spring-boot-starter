package lc.cy.cache.caffine;


import com.github.benmanes.caffeine.cache.Cache;
import lc.cy.cache.config.CacheRedisCaffeineProperties;
import lc.cy.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RedisCaffeineCache extends AbstractValueAdaptingCache {

    private final Logger logger = LoggerFactory.getLogger(RedisCaffeineCache.class);

    private static final char COMMA = ',';

    private String name;

    private RedisTemplate<Object, Object> redisTemplate;

    private Cache<Object, Object> caffeineCache;

    private String cachePrefix;

    // redis默认的缓存失效时间（单位:毫秒）
    private long redisExpires;

    private String topic;

    private CacheRedisCaffeineProperties caffeineProperties;

    protected RedisCaffeineCache(boolean allowNullValues) {
        super(allowNullValues);
    }

    public RedisCaffeineCache(String name, RedisTemplate<Object, Object> redisTemplate,
                              Cache<Object, Object> caffeineCache, CacheRedisCaffeineProperties cacheRedisCaffeineProperties) {
        super(cacheRedisCaffeineProperties.isAllowNull());
        this.name = name;
        this.redisTemplate = redisTemplate;
        this.caffeineCache = caffeineCache;
        this.caffeineProperties = cacheRedisCaffeineProperties;
        this.cachePrefix = cacheRedisCaffeineProperties.getCachePrefix();
        this.redisExpires = cacheRedisCaffeineProperties.getRedis().getDefaultExpires();
        Map<String, Long> expires = cacheRedisCaffeineProperties.getRedis().getExpires();
        if(expires != null && !expires.isEmpty() && expires.containsKey(name)) {
            this.redisExpires = expires.get(name).longValue();
        }
        this.topic = cacheRedisCaffeineProperties.getRedis().getTopic();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = lookup(key);
        if (value != null) {
            return (T) value;
        }

        ReentrantLock lock = new ReentrantLock();
        try {
            lock.lock();
            value = lookup(key);
            if (value != null) {
                return (T) value;
            }
            value = valueLoader.call();
            Object storeValue = toStoreValue(value);
            put(key, storeValue);
            return (T) value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e.getCause());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (!super.isAllowNullValues() && value == null) {
            this.evict(key);
            return;
        }
        long expire = getRedisExpire(key);
        if (expire > 0) {
            redisTemplate.opsForValue().set(redisKey(key), toStoreValue(value), expire, TimeUnit.MILLISECONDS);
        } else {
            redisTemplate.opsForValue().set(redisKey(key), toStoreValue(value));
        }
        push(JsonUtil.toJson(new CacheMessage(this.name, key)));

        caffeineCache.put(key, toStoreValue(value));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        Object cacheKey = redisKey(key);
        Object prevValue = null;
        ReentrantLock lock = new ReentrantLock();
        try {
            // 此处可能在多实例下会有同时设置动作，可以接受
            lock.lock();
            prevValue = redisTemplate.opsForValue().get(cacheKey);
            if (prevValue == null) {
                long expire = getRedisExpire(key);
                if (expire > 0) {
                    redisTemplate.opsForValue().set(redisKey(key), toStoreValue(value), expire, TimeUnit.MILLISECONDS);
                } else {
                    redisTemplate.opsForValue().set(redisKey(key), toStoreValue(value));
                }

                push(JsonUtil.toJson(new CacheMessage(this.name, key)));

                caffeineCache.put(key, toStoreValue(value));
            }
        } finally {
            lock.unlock();
        }
        return toValueWrapper(prevValue);
    }

    @Override
    public void evict(Object key) {
        // 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
        redisTemplate.delete(redisKey(key));

        push(JsonUtil.toJson(new CacheMessage(this.name, key)));

        caffeineCache.invalidate(key);
    }

    @Override
    protected Object lookup(Object key) {

        Object value = caffeineCache.getIfPresent(key);
        if (value != null) {
            logger.info("load from caffeine,name:{},key:{},value:{}", this.name, key, value);
            return value;
        }

        Object cacheKey = redisKey(key);
        value = redisTemplate.opsForValue().get(cacheKey);
        if (value != null) {
            logger.debug("load from redis and put in caffeine, the redisKey is:{},value:{}", cacheKey, value);
            caffeineCache.put(key, value);
        }
        return value;
    }

    private Object redisKey(Object key) {
        String realKey = key.toString();
        String prefix = this.cachePrefix;
        // 如果不包含逗号
        return this.name.concat(":").concat(StringUtils.isEmpty(prefix) ? realKey : prefix.concat(":").concat(realKey));
    }

    private long getRedisExpire(Object key) {
        return redisExpires;
    }

    /**
     * @description 缓存变更时通知其他节点清理本地缓存
     * @version 1.0.0
     * @param message
     */
    private void push(String message) {
        redisTemplate.convertAndSend(topic, message);
    }

    @Override
    public void clear() {
        // 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
        Set<Object> keys = redisTemplate.keys(this.name.concat(":*"));
        if(keys != null && !keys.isEmpty()) {
            logger.info("clear redis cache, the keys is:{}", keys.stream().map(x -> x.toString()).collect(Collectors.joining(",")));
            redisTemplate.delete(keys);
        }

        push(JsonUtil.toJson(new CacheMessage(this.name, null)));

        caffeineCache.invalidateAll();
    }

    /**
     * @description 清理本地缓存
     * @version 1.0.0
     * @param key
     */
    public void clearLocal(Object key) {
        logger.info("clear local cache, the key is : {}", key);
        if (key == null) {
            clearLocal();
        } else {
            caffeineCache.invalidate(key);
        }
    }

    /**
     * 清除本地所有缓存
     */
    public void clearLocal() {
        logger.info("clear local all cache");
        caffeineCache.invalidateAll();
    }

    /**
     * 清除redis缓存
     * @param prefix
     */
    public void clearRedis(String prefix) {
        // 先查找
        Assert.hasText(prefix, "'prefix' must has value!");
        // 判断不出来是哪种模式，尝试一下集群模式
        String pattern = name + ":" + (prefix.endsWith("*") ? prefix : prefix + "*");

        Set<Object> keys = redisTemplate.keys(pattern);
        if (null != keys && !keys.isEmpty()) {
            logger.info("clear redis cache, the keys is:{}", keys.stream().map(x -> x.toString()).collect(Collectors.joining(",")));
            redisTemplate.delete(keys);
        }
    }
}