export const MAX_BIN_UPLOAD_BYTES = 200 * 1024 * 1024;
export const MAX_BIN_UPLOAD_MB = 200;

export type BinValidationResult = "ok" | "invalid_extension" | "too_large";

export function isBinTelemetryFile(file: File): boolean {
  return file.name.toLowerCase().endsWith(".bin");
}

export function validateBinTelemetryFile(file: File): BinValidationResult {
  if (!isBinTelemetryFile(file)) {
    return "invalid_extension";
  }
  if (file.size > MAX_BIN_UPLOAD_BYTES) {
    return "too_large";
  }
  return "ok";
}
