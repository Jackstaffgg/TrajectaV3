export type FlightFrame = {
  t: number;
  lat: number;
  lon: number;
  alt: number;
  speed?: number;
  roll?: number;
  pitch?: number;
  yaw?: number;
  battery?: number;
  accelX?: number;
  accelY?: number;
  accelZ?: number;
  gyroX?: number;
  gyroY?: number;
  gyroZ?: number;
  vibration?: number;
  pressure?: number;
  gpsLat?: number;
  gpsLon?: number;
  gpsAlt?: number;
  positionErrorM?: number;
  climbRate?: number;
};

export type FlightEvent = {
  id?: string;
  eventId?: number;
  t: number;
  type: string;
  code?: string;
  severity?: "info" | "warning" | "critical" | string;
  message?: string;
  value?: string | number | boolean | null;
  lat?: number;
  lon?: number;
  alt?: number;
};

export type FlightMetrics = {
  maxAltitude?: number;
  maxSpeed?: number;
  flightDurationSec?: number;
  totalDistanceMeters?: number;
  horizontalDistanceMeters?: number;
  totalDistance3dMeters?: number;
  maxVerticalSpeed?: number;
  imuRateHz?: number;
  imuSamplingPeriodMs?: number;
  usedImuCount?: number;
  usedBaroCount?: number;
  endpointErrorM?: number;
  rmsCrosstrackErrorM?: number;
  ateM?: number;
  p95CrosstrackErrorM?: number;
  pointCount?: number;
  groundTruthUsed?: boolean;
  maxSensorGpsErrorM?: number;
  meanSensorGpsErrorM?: number;
  peakAbsAccelM?: number;
  peakAbsGyroRadSec?: number;
  maxVibration?: number;
};

export type AuthUser = {
  id: number;
  name: string;
  username: string;
  email: string;
  role: string;
  hasActiveBan?: boolean;
};

export type UserPunishmentInfo = {
  id: number;
  userId: number;
  punishedById: number;
  type: "BAN" | string;
  reason: string;
  createdAt: string;
  expiredAt?: string | null;
  expired: boolean;
};

export type AdminUserDetails = AuthUser & {
  activePunishments: UserPunishmentInfo[];
  punishmentHistory: UserPunishmentInfo[];
};

export type AdminUsersPage = {
  items: AuthUser[];
  page: number;
  size: number;
  hasNext: boolean;
};

export type AdminUserStats = {
  totalUsers: number;
  usersCount: number;
  adminsCount: number;
  ownersCount: number;
  staffCount: number;
  bannedUsers: number;
};

export type AuthState = {
  isAuthenticated: boolean;
  token: string;
  user: AuthUser | null;
};

export type TaskStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "CANCELLED";

export type TaskInfo = {
  id: number;
  title: string;
  folderName?: string | null;
  folderId?: number | null;
  status: TaskStatus;
  errorMessage?: string | null;
  aiConclusion?: string | null;
  aiModel?: string | null;
  sourceTaskId?: number | null;
  analysisParams?: Record<string, unknown>;
};

export type TaskComparisonItem = {
  taskId: number;
  title: string;
  folderName?: string | null;
  status: TaskStatus | string;
  metrics: Record<string, number | null | undefined>;
  delta: Record<string, number | null | undefined>;
};

export type TaskComparisonInfo = {
  baselineTaskId: number;
  items: TaskComparisonItem[];
};

export type TaskFolderInfo = {
  id: number;
  name: string;
  parentId?: number | null;
  createdAt?: string;
  updatedAt?: string;
  children?: TaskFolderInfo[];
};

export type TaskSocketPayload = {
  taskId: number;
  taskStatus: TaskStatus;
  taskTitle?: string;
  message?: string | null;
  timestamp?: string;
};

export type NotificationInfo = {
  id: number;
  type: string;
  content: string;
  senderId?: number | null;
  senderName?: string | null;
  recipientId?: number | null;
  referenceId?: number | null;
  isRead: boolean;
  createdAt: string;
};

export type NotificationSocketPayload = {
  notification: NotificationInfo;
};

export type SocketEvent = {
  type: "NEW_NOTIFICATION" | "TASK_STATUS_UPDATE" | "USER_BANNED" | "USER_UNBANNED";
  payload: unknown;
};

export type UserUnbannedSocketPayload = {
  userId: number;
  timestamp?: string;
};

export type UserBannedSocketPayload = {
  userId: number;
  punishmentId: number;
  reason: string;
  expiredAt?: string | null;
  punishedById: number;
  punishedByName: string;
  timestamp?: string;
};

export type UserProfileUpdateInput = {
  name?: string;
  username?: string;
  email?: string;
  role?: string;
  password?: string;
  oldPassword?: string;
};

export type FlightMetadata = {
  logName?: string;
  vehicleType?: string;
  parserVersion?: string;
  gpsUnits?: string;
  source?: string;
  imuSamplingHz?: number;
  imuSamplingPeriodMs?: number;
  imuResampledStepMs?: number;
  [key: string]: unknown;
};

export type FlightLogData = {
  metadata: FlightMetadata;
  frames: FlightFrame[];
  events: FlightEvent[];
  params: Record<string, string | number | boolean | null>;
  metrics: FlightMetrics;
  aiConclusion?: string;
  aiModel?: string;
};

export type ReplayCameraMode = "chase" | "fpv" | "free";

export type AnalysisMode =
  | "tasks"
  | "analytics"
  | "compare"
  | "profile"
  | "admin-users"
  | "admin-user"
  | "admin-notifications";
