package dev.knalis.trajectaapi.service.impl.cache;

import dev.knalis.trajectaapi.model.user.punishment.PunishmentType;
import dev.knalis.trajectaapi.repo.PunishmentRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PunishmentCacheService {

    private static final String BAN_FLAG_CACHE = "punishmentBanFlagByUserV1";

    private final CacheManager cacheManager;
    private final PunishmentRepository punishmentRepository;

    public PunishmentCacheService(CacheManager cacheManager, PunishmentRepository punishmentRepository) {
        this.cacheManager = cacheManager;
        this.punishmentRepository = punishmentRepository;
    }

    public boolean isUserBanned(long userId) {
        Boolean cached = getBanFlag(userId);
        if (cached != null) {
            return cached;
        }

        boolean isBanned = punishmentRepository.existsActivePunishmentByUserId(userId, PunishmentType.BAN, Instant.now());
        putBanFlag(userId, isBanned);
        return isBanned;
    }

    public Boolean getBanFlag(long userId) {
        Cache cache = cacheManager.getCache(BAN_FLAG_CACHE);
        if (cache == null) {
            return null;
        }

        Cache.ValueWrapper wrapper = cache.get(userId);
        if (wrapper == null) {
            return null;
        }

        Object value = wrapper.get();
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String str) {
            if ("true".equalsIgnoreCase(str)) {
                return true;
            }
            if ("false".equalsIgnoreCase(str)) {
                return false;
            }
        }

        cache.evict(userId);
        return null;
    }

    public void putBanFlag(long userId, boolean isBanned) {
        Cache cache = cacheManager.getCache(BAN_FLAG_CACHE);
        if (cache != null) {
            cache.put(userId, isBanned);
        }
    }

    public void evictBanFlag(Long userId) {
        if (userId == null) {
            return;
        }
        Cache cache = cacheManager.getCache(BAN_FLAG_CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
