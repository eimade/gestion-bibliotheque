import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import {
  ArchiveCheck,
  ContactRequest,
  MemberCreateRequest,
  MemberDetail,
  MemberSearchQuery,
  MemberSummary,
  MemberToArchive,
  MemberUpdateRequest,
  MyMember,
} from './member.models';

/** Gestion des adhérents par le personnel (US-016 → US-022). */
@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/members`;

  search(query: MemberSearchQuery): Observable<Page<MemberSummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size).set('sort', query.sort);
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    return this.http.get<Page<MemberSummary>>(this.url, { params });
  }

  get(memberNumber: string): Observable<MemberDetail> {
    return this.http.get<MemberDetail>(`${this.url}/${memberNumber}`);
  }

  register(body: MemberCreateRequest): Observable<MemberDetail> {
    return this.http.post<MemberDetail>(this.url, body);
  }

  update(memberNumber: string, body: MemberUpdateRequest): Observable<MemberDetail> {
    return this.http.put<MemberDetail>(`${this.url}/${memberNumber}`, body);
  }

  renew(memberNumber: string): Observable<MemberDetail> {
    return this.http.post<MemberDetail>(`${this.url}/${memberNumber}/renewal`, {});
  }

  suspend(memberNumber: string, reason: string): Observable<MemberDetail> {
    return this.http.post<MemberDetail>(`${this.url}/${memberNumber}/suspension`, { reason });
  }

  reactivate(memberNumber: string): Observable<MemberDetail> {
    return this.http.post<MemberDetail>(`${this.url}/${memberNumber}/reactivation`, {});
  }

  archiveCheck(memberNumber: string): Observable<ArchiveCheck> {
    return this.http.get<ArchiveCheck>(`${this.url}/${memberNumber}/archive-check`);
  }

  archive(memberNumber: string): Observable<MemberDetail> {
    return this.http.post<MemberDetail>(`${this.url}/${memberNumber}/archive`, {});
  }

  toArchive(page: number, size: number): Observable<Page<MemberToArchive>> {
    return this.http.get<Page<MemberToArchive>>(`${this.url}/to-archive`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }
}

/** Espace de l'adhérent connecté (US-023, US-024) : jamais d'identifiant d'adhérent dans l'URL (AC-023-4). */
@Injectable({ providedIn: 'root' })
export class MemberSpaceService {
  private readonly http = inject(HttpClient);

  me(): Observable<MyMember> {
    return this.http.get<MyMember>(`${API_BASE}/me/member`);
  }

  updateContact(body: ContactRequest): Observable<MyMember> {
    return this.http.put<MyMember>(`${API_BASE}/me/contact`, body);
  }
}
