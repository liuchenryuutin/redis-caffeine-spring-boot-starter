package lc.cy.cache.caffine;

/**
 * 类名称： <br>
 * 类描述： <br>
 *
 * @Date: 2023/11/10 11:00 <br>
 * @author: liuchen11
 */

import com.github.benmanes.caffeine.cache.Caffeine;
import lc.cy.cache.config.CacheRedisCaffeineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class RedisCaffeineCacheManager implements CacheManager {

    private final Logger logger = LoggerFactory.getLogger(RedisCaffeineCacheManager.class);

    private ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    private CacheRedisCaffeineProperties cacheRedisCaffeineProperties;

    private RedisTemplate<Object, Object> redisTemplate;

    private boolean dynamic = true;

    private Set<String> cacheNames;

    public RedisCaffeineCacheManager(CacheRedisCaffeineProperties cacheRedisCaffeineProperties,
                                     RedisTemplate<Object, Object> redisTemplate) {
        super();
        this.cacheRedisCaffeineProperties = cacheRedisCaffeineProperties;
        this.redisTemplate = redisTemplate;
        this.dynamic = cacheRedisCaffeineProperties.isDynamic();
        this.cacheNames = cacheRedisCaffeineProperties.getCacheNames();
    }

    @Override
    public Cache getCache(String name) {
        if(!StringUtils.isEmpty(name)) {
            return null;
        }
        Cache cache = cacheMap.get(name);
        if (cache != null) {
            return cache;
        }
        if (!dynamic && !cacheNames.contains(name)) {
            return cache;
        }

        cache = new RedisCaffeineCache(name, redisTemplate, caffeineCache(name), cacheRedisCaffeineProperties);
        Cache oldCache = cacheMap.putIfAbsent(name, cache);
        logger.debug("create cache instance, the cache name is : {}", name);
        return oldCache == null ? cache : oldCache;
    }

    public com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache(String name) {
        Map<String, CacheRedisCaffeineProperties.Caffeine> caffeineMap = cacheRedisCaffeineProperties.getCaffeine();
        CacheRedisCaffeineProperties.Caffeine caffeineConfig = caffeineMap.get(name);
        if (caffeineConfig == null) {
            caffeineConfig = new CacheRedisCaffeineProperties.Caffeine();
        }
        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        if (caffeineConfig.getExpireAfterAccess() > 0) {
            cacheBuilder.expireAfterAccess(caffeineConfig.getExpireAfterAccess(), TimeUnit.MILLISECONDS);
        }
        if (caffeineConfig.getExpireAfterWrite() > 0) {
            cacheBuilder.expireAfterWrite(caffeineConfig.getExpireAfterWrite(), TimeUnit.MILLISECONDS);
        }
        if (caffeineConfig.getInitialCapacity() > 0) {
            cacheBuilder.initialCapacity(caffeineConfig.getInitialCapacity());
        }
        if (caffeineConfig.getMaximumSize() > 0) {
            cacheBuilder.maximumSize(caffeineConfig.getMaximumSize());
        }
        if (caffeineConfig.getRefreshAfterWrite() > 0) {
            cacheBuilder.refreshAfterWrite(caffeineConfig.getRefreshAfterWrite(), TimeUnit.MILLISECONDS);
        }
        return cacheBuilder.build();
    }

    @Override
    public Collection<String> getCacheNames() {
        return this.cacheNames;
    }

    public void clearLocal(String cacheName, Object key) {
        if(StringUtils.isEmpty(cacheName)) {
            return;
        }
        Cache cache = cacheMap.get(cacheName);
        if (cache == null) {
            return;
        }

        RedisCaffeineCache redisCaffeineCache = (RedisCaffeineCache) cache;
        redisCaffeineCache.clearLocal(key);
    }

    public void clearRedis(String cacheName, String prefix) {
        // 先查找
        Assert.hasText(prefix, "'prefix' must has value!");
        // 判断不出来是哪种模式，尝试一下集群模式
        String pattern = (cacheName + ":" + prefix + "*");
        RedisClusterConnection conn = null;
        try {
            conn = redisTemplate.getConnectionFactory().getClusterConnection();

            Set<byte[]> keys = conn.keys(pattern.getBytes());
            logger.info("Got keys for key:{},size:{}", pattern, keys.size());
            // 先转换下
            Set<Object> sets = new HashSet<>();
            Iterator<byte[]> iter = keys.iterator();
            while (iter.hasNext()) {
                String key = new String(iter.next());
                logger.info(" Start to del key:{}", key);
                sets.add(key);
            }
            redisTemplate.delete(sets);
            return;
        } catch (Exception e) {
            logger.error("It is not cluster mode!{},{}", cacheName, prefix, e);
        } finally {
            if (null != conn)
                conn.close();
        }
        Set<Object> keys = redisTemplate.keys(cacheName + ":" + prefix + "*");
        logger.info("Got keys for key:{},size:{}", pattern, keys.size());
        if (null != keys && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void clearCaffeine(String cacheName) {

        Cache cache = cacheMap.get(cacheName);
        if (cache == null) {
            return;
        }
        RedisCaffeineCache redisCaffeineCache = (RedisCaffeineCache) cache;
        redisCaffeineCache.clearAllLocal();
    }

}