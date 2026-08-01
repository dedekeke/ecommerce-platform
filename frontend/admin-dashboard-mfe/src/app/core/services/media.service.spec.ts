import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { MediaService } from './media.service';
import { MediaResponse, MediaUploadEvent } from '../models/media.model';

const mockMedia: MediaResponse = {
  id: 'media-1',
  filename: 'product.png',
  contentType: 'image/png',
  size: 1024,
  downloadUrl: '/api/media/media-1/download',
  contentUrl: '/api/media/media-1/content',
  thumbnailUrl: '/storage/thumbnails/thumb_media-1.png',
  dimensions: { width: 200, height: 200 },
  uploadedBy: 'user-1',
  createdAt: '2024-01-01T00:00:00Z',
};

describe('MediaService', () => {
  let service: MediaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MediaService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MediaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should POST a multipart form with the file to /api/media/upload', () => {
    const file = new File(['data'], 'product.png', { type: 'image/png' });
    service.upload(file).subscribe();

    const req = httpMock.expectOne('/api/media/upload');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(mockMedia);
  });

  it('should emit progress events while the upload is in flight', () => {
    const file = new File(['data'], 'product.png', { type: 'image/png' });
    const events: MediaUploadEvent[] = [];
    service.upload(file).subscribe((event) => events.push(event));

    const req = httpMock.expectOne('/api/media/upload');
    req.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 100 });
    req.flush(mockMedia);

    expect(events[0]).toEqual({ type: 'progress', progress: 50 });
  });

  it('should emit a complete event carrying the public contentUrl when the upload finishes', () => {
    const file = new File(['data'], 'product.png', { type: 'image/png' });
    let result: MediaUploadEvent | undefined;
    service.upload(file).subscribe((event) => {
      if (event.type === 'complete') {
        result = event;
      }
    });

    const req = httpMock.expectOne('/api/media/upload');
    req.flush(mockMedia);

    expect(result).toEqual({ type: 'complete', media: mockMedia });
    expect((result as { media: MediaResponse }).media.contentUrl).toBe('/api/media/media-1/content');
  });

  it('should propagate an error when the upload fails', () => {
    const file = new File(['data'], 'product.png', { type: 'image/png' });
    let error: unknown;
    service.upload(file).subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne('/api/media/upload');
    req.flush({ message: 'File type not allowed' }, { status: 400, statusText: 'Bad Request' });

    expect(error).toBeTruthy();
  });
});
