import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { OverviewComponent } from './features/overview/overview.component';
import { ChatComponent } from './features/chat/chat.component';
import { HistoryComponent } from './features/history/history.component';
import { DocumentsComponent } from './features/documents/documents.component';
import { AuditComponent } from './features/audit/audit.component';
import { SettingsComponent } from './features/settings/settings.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { 
    path: 'overview', 
    component: OverviewComponent, 
    canActivate: [authGuard] 
  },
  { 
    path: 'chat', 
    component: ChatComponent, 
    canActivate: [authGuard] 
  },
  { 
    path: 'history', 
    component: HistoryComponent, 
    canActivate: [authGuard] 
  },
  { 
    path: 'documents', 
    component: DocumentsComponent, 
    canActivate: [authGuard, roleGuard],
    data: { expectedRoles: ['ROLE_ADMIN'] }
  },
  { 
    path: 'admin/audit', 
    component: AuditComponent, 
    canActivate: [authGuard, roleGuard],
    data: { expectedRoles: ['ROLE_ADMIN'] }
  },
  { 
    path: 'settings', 
    component: SettingsComponent, 
    canActivate: [authGuard] 
  },
  { path: 'investigation', redirectTo: 'chat', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
