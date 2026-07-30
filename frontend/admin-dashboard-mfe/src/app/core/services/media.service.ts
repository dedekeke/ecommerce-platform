import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEventType, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { MediaResponse, MediaUploadEvent } from '../models/media.model';

@Injectable({ providedIn: 'root' })
export class MediaService {
  private readonly http = inject(HttpClient);
  private readonly uploadUrl = '/api/media/upload';

  upload(file: File): Observable<MediaUploadEvent> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http
      .post<MediaResponse>(this.uploadUrl, formData, { reportProgress: true, observe: 'events' })
      .pipe(
        filter((event) => event.type === HttpEventType.UploadProgress || event.type === HttpEventType.Response),
        map((event): MediaUploadEvent => {
          if (event.type === HttpEventType.UploadProgress) {
            const total = event.total ?? event.loaded;
            return { type: 'progress', progress: total > 0 ? Math.round((event.loaded / total) * 100) : 0 };
          }
          return { type: 'complete', media: (event as HttpResponse<MediaResponse>).body as MediaResponse };
        })
      );
  }
}
