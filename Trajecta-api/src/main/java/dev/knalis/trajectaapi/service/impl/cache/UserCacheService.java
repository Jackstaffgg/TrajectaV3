package dev.knalis.trajectaapi.service.impl.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.trajectaapi.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserCacheService {

    private static final String USER_BY_ID_CACHE = "userByIdV1";
    private static final String USER_BY_USERNAME_CACHE = "userByUsernameV1";
    private static final String USER_PAGE_CACHE = "userPageV1";

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public User getById(long userId) {
        return readCachedUser(USER_BY_ID_CACHE, userId);
    }

    public User getByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return readCachedUser(USER_BY_USERNAME_CACHE, username.toLowerCase(Locale.ROOT));
    }

    public List<User> getPage(int page, int size) {
        return readCachedUserList(page + ":" + size);
    }

    public void putUser(User user) {
        if (user == null || user.getId() == null || user.getUsername().isBlank()) {
            return;
        }
        putCacheValue(USER_BY_ID_CACHE, user.getId(), user);
        putCacheValue(USER_BY_USERNAME_CACHE, user.getUsername().toLowerCase(Locale.ROOT), user);
    }

    public void putPage(int page, int size, List<User> users) {
        if (users == null) {
            return;
        }
        putCacheValue(USER_PAGE_CACHE, page + ":" + size, users);
    }

    public void evictUser(Long userId, String username) {
        if (userId != null) {
            evictCacheKey(USER_BY_ID_CACHE, userId);
        }
        if (username != null && !username.isBlank()) {
            evictCacheKey(USER_BY_USERNAME_CACHE, username.toLowerCase(Locale.ROOT));
        }
    }

    public void evictUserPage() {
        Cache userPageCache = cacheManager.getCache(USER_PAGE_CACHE);
        if (userPageCache != null) {
            userPageCache.clear();
        }
    }

    private User readCachedUser(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            return null;
        }

        Object value = wrapper.get();
        if (value instanceof User user) {
            return user;
        }
        if (value instanceof Map<?, ?> mapValue) {
            User converted = convertUserMap(mapValue);
            if (converted == null) {
                evictCacheKey(cacheName, key);
            }
            return converted;
        }
        return null;
    }

    private List<User> readCachedUserList(Object key) {
        Cache cache = cacheManager.getCache(USER_PAGE_CACHE);
        if (cache == null) {
            return null;
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null || wrapper.get() == null) {
            return null;
        }

        Object value = wrapper.get();
        if (!(value instanceof List<?> listValue)) {
            return null;
        }

        List<User> users = new ArrayList<>(listValue.size());
        for (Object item : listValue) {
            if (item instanceof User user) {
                users.add(user);
            } else if (item instanceof Map<?, ?> mapValue) {
                User converted = convertUserMap(mapValue);
                if (converted == null) {
                    evictCacheKey(USER_PAGE_CACHE, key);
                    return null;
                }
                users.add(converted);
            } else {
                evictCacheKey(USER_PAGE_CACHE, key);
                return null;
            }
        }
        return users;
    }

    private User convertUserMap(Map<?, ?> mapValue) {
        try {
            return objectMapper
                    .copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .convertValue(mapValue, User.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void putCacheValue(String cacheName, Object key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void evictCacheKey(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
