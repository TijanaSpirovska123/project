import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';
import { AiInsightResponse, GenerateAiInsightRequest } from '../../models/insights/ai-insight.model';

@Injectable({
  providedIn: 'root',
})
export class AiInsightsService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('insights/ai', http);
  }

  analyze(body: GenerateAiInsightRequest): Observable<{ data: AiInsightResponse; success: boolean; error?: string }> {
    return this.http.post<{ data: AiInsightResponse; success: boolean; error?: string }>(
      `${this.URL}/analyze`,
      body,
    );
  }
}
