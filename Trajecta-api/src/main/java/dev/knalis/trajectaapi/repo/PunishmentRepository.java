package dev.knalis.trajectaapi.repo;

import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.model.user.punishment.PunishmentType;
import dev.knalis.trajectaapi.model.user.punishment.UserPunishment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PunishmentRepository extends JpaRepository<UserPunishment, Long> {
    
    @Query("""
        select case when count(p) > 0 then true else false end
        from UserPunishment p
        where p.user = :user
          and p.type = :type
          and (p.expiredAt is null or p.expiredAt > :now)
    """)
    boolean existsActivePunishment(
            @Param("user") User user,
            @Param("type") PunishmentType type,
            @Param("now") Instant now
    );
    
    @Query("""
        select p
        from UserPunishment p
        join fetch p.user
        join fetch p.punishedBy
        where p.user = :user
          and (p.expiredAt is null or p.expiredAt > :now)
        order by p.createdAt desc
    """)
    List<UserPunishment> findActivePunishments(
            @Param("user") User user,
            @Param("now") Instant now
    );

    @Query("""
        select p
        from UserPunishment p
        join fetch p.user
        join fetch p.punishedBy
        where p.user = :user
          and p.type = :type
          and (p.expiredAt is null or p.expiredAt > :now)
        order by p.createdAt desc
    """)
    Optional<UserPunishment> findLatestActivePunishment(
            @Param("user") User user,
            @Param("type") PunishmentType type,
            @Param("now") Instant now
    );

    @Query("""
        select p
        from UserPunishment p
        join fetch p.user
        join fetch p.punishedBy
        where p.user = :user
        order by p.createdAt desc
    """)
    List<UserPunishment> findByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("""
        select case when count(p) > 0 then true else false end
        from UserPunishment p
        where p.user.id = :userId
          and p.type = :type
          and (p.expiredAt is null or p.expiredAt > :now)
    """)
    boolean existsActivePunishmentByUserId(
            @Param("userId") long userId,
            @Param("type") PunishmentType type,
            @Param("now") Instant now
    );

    @Query("""
        select count(distinct p.user.id)
        from UserPunishment p
        where p.type = :type
          and (p.expiredAt is null or p.expiredAt > :now)
    """)
    long countDistinctUsersWithActivePunishmentByType(
            @Param("type") PunishmentType type,
            @Param("now") Instant now
    );
}