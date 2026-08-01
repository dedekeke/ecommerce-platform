export interface MediaResponse {
  id: string;
  filename: string;
  contentType: string;
  size: number;
  downloadUrl: string;
  /** Public, unauthenticated read path — safe for plain <img> tags. */
  contentUrl: string;
  thumbnailUrl?: string;
  dimensions?: {
    width: number;
    height: number;
  };
  uploadedBy: string;
  createdAt: string;
}

export type MediaUploadEvent =
  | { type: 'progress'; progress: number }
  | { type: 'complete'; media: MediaResponse };

// Mirrors media-service `media.allowed-file-types` (application.properties).
export const ALLOWED_IMAGE_MIME_TYPES = [
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'image/svg+xml',
] as const;

// Mirrors media-service `spring.servlet.multipart.max-file-size` (10MB).
export const MAX_IMAGE_FILE_SIZE_BYTES = 10 * 1024 * 1024;
