package dev.knalis.trajectaapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated user statistics for admin dashboard counters.")
public class AdminUserStatsResponse {

    @Schema(description = "Total users count.", example = "154")
    private long totalUsers;

    @Schema(description = "Users with role USER.", example = "140")
    private long usersCount;

    @Schema(description = "Users with role ADMIN.", example = "12")
    private long adminsCount;

    @Schema(description = "Users with role OWNER.", example = "2")
    private long ownersCount;

    @Schema(description = "Total privileged accounts (ADMIN + OWNER).", example = "14")
    private long staffCount;

    @Schema(description = "Users with an active BAN punishment.", example = "5")
    private long bannedUsers;
}

