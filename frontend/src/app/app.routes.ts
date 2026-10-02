import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { ChatComponent } from './features/chat/chat.component';
import { HistoryComponent } from './features/history/history.component';
import { DocumentsComponent } from './features/documents/documents.component';
import { AuditComponent } from './features/audit/audit.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'chat', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
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
  { path: 'investigation', redirectTo: 'chat', pathMatch: 'full' },
  { path: '**', redirectTo: 'chat' }
];
