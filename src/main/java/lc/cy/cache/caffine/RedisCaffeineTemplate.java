package lc.cy.cache.caffine;

/**
 * 类名称： <br>
 * 类描述： <br>
 *
 * @Date: 2023/11/15 09:24 <br>
 * @author: liuchen11
 */
public class RedisCaffeineTemplate {

    private RedisCaffeineCacheManager redisCaffeineManager;

    public RedisCaffeineTemplate(RedisCaffeineCacheManager redisCaffeineCacheManager) {
        this.redisCaffeineManager = redisCaffeineManager;
    }

    /**
     * @param prefix
     */
    public void clearRedisKeys(String cacheName, String prefix) {
        redisCaffeineManager.clearRedis(cacheName, prefix);

    }

    /**
     * 清除caffeine缓存
     *
     * @param cacheName
     */
    public void clearCaffeine(String cacheName) {
        redisCaffeineManager.clearCaffeine(cacheName);
    }

    /**
     * 失效缓存
     *
     * @param cacheName
     * @param key
     */
    public void evict(String cacheName, String key) {
        redisCaffeineManager.getCache(cacheName).evict(key);
    }

    /**
     * 存入缓存
     *
     * @param cacheName
     * @param key
     * @param value
     */
    public void put(String cacheName, String key, Object value) {
        redisCaffeineManager.getCache(cacheName).put(key, value);
    }
}
