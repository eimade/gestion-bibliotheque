import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import { BookPublicDetail, BookSearchItem, CategoryOption, LanguageOption, PublicSearchQuery } from './catalog.models';

/** Catalogue public, sans connexion (US-007, US-008). */
@Injectable({ providedIn: 'root' })
export class CatalogSearchService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/catalog`;
  private languages$: Observable<readonly LanguageOption[]> | null = null;

  search(query: PublicSearchQuery): Observable<Page<BookSearchItem>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.categoryId !== null) {
      params = params.set('categoryId', query.categoryId);
    }
    if (query.language) {
      params = params.set('language', query.language);
    }
    if (query.available) {
      params = params.set('available', true);
    }
    if (query.sort !== 'relevance') {
      params = params.set('sort', query.sort);
    }
    return this.http.get<Page<BookSearchItem>>(`${this.url}/books`, { params });
  }

  book(id: number): Observable<BookPublicDetail> {
    return this.http.get<BookPublicDetail>(`${this.url}/books/${id}`);
  }

  categories(): Observable<readonly CategoryOption[]> {
    return this.http.get<readonly CategoryOption[]>(`${this.url}/categories`);
  }

  /** Liste fixe : chargée une fois par session. */
  languages(): Observable<readonly LanguageOption[]> {
    this.languages$ ??= this.http.get<readonly LanguageOption[]>(`${this.url}/languages`).pipe(shareReplay(1));
    return this.languages$;
  }
}
