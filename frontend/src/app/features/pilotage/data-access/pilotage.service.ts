import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import { AuditEntry, AuditQuery, Dashboard, MonthlyStatistics, SettingView } from './pilotage.models';

/** Pilotage (US-041 → US-044) : tableau de bord, statistiques et export, paramètres, journal d'audit. */
@Injectable({ providedIn: 'root' })
export class PilotageService {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${API_BASE}/dashboard`);
  }

  statistics(month: string): Observable<MonthlyStatistics> {
    return this.http.get<MonthlyStatistics>(`${API_BASE}/statistics`, { params: new HttpParams().set('month', month) });
  }

  exportStatistics(month: string): Observable<Blob> {
    return this.http.get(`${API_BASE}/statistics/export`, {
      params: new HttpParams().set('month', month),
      responseType: 'blob',
    });
  }

  settings(): Observable<readonly SettingView[]> {
    return this.http.get<readonly SettingView[]>(`${API_BASE}/settings`);
  }

  updateSetting(code: string, value: number, version: number): Observable<SettingView> {
    return this.http.put<SettingView>(`${API_BASE}/settings/${code}`, { value, version });
  }

  audit(query: AuditQuery): Observable<Page<AuditEntry>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size).set('from', query.from).set('to', query.to);
    if (query.object.trim()) {
      params = params.set('object', query.object.trim());
    }
    if (query.actor.trim()) {
      params = params.set('actor', query.actor.trim());
    }
    if (query.action) {
      params = params.set('action', query.action);
    }
    return this.http.get<Page<AuditEntry>>(`${API_BASE}/audit-entries`, { params });
  }
}
