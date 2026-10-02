import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { InvestigationComponent } from './features/investigation/investigation.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'investigation', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'investigation', component: InvestigationComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'investigation' }
];
