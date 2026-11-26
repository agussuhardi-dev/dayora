import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from './api';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `<main class="auth-page">
    <section class="intro" aria-label="Dayora">
      <a class="brand" routerLink="/" aria-label="Dayora home"><span class="brand-mark">D</span>Dayora</a>
      <div class="intro-copy"><span class="eyebrow">Plan with clarity</span><h2>Make space for what matters.</h2><p>Shape your day, build better routines, and move every goal forward.</p></div>
      <p class="quote">A calmer day starts with a clear plan.</p>
    </section>
    <section class="form-panel">
      <form class="glass-surface" (ngSubmit)="submit()" aria-labelledby="title">
        <header><span class="mobile-brand"><span class="brand-mark">D</span>Dayora</span><p class="kicker">Welcome back</p><h1 id="title">Sign in to Dayora</h1><p class="subtitle">Continue building your best days.</p></header>
        @if (registered) { <p class="notice success" role="status">Account created. Sign in to get started.</p> }
        <mat-form-field appearance="outline"><mat-label>Username</mat-label><input matInput name="username" [(ngModel)]="username" required autocomplete="username"></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Password</mat-label><input matInput name="password" [(ngModel)]="password" [type]="showPassword ? 'text' : 'password'" required autocomplete="current-password"><button matSuffix type="button" class="reveal" (click)="showPassword=!showPassword" [attr.aria-label]="showPassword ? 'Hide password' : 'Show password'">{{showPassword ? 'Hide' : 'Show'}}</button></mat-form-field>
        @if (error) { <p class="notice error" role="alert">{{error}}</p> }
        <button class="submit" mat-flat-button type="submit" [disabled]="busy">{{busy ? 'Signing in…' : 'Sign in'}}</button>
        <p class="switch">New to Dayora? <a routerLink="/register">Create an account</a></p>
      </form>
    </section>
  </main>`,
  styles: [`
    :host{display:block}.auth-page{min-height:100dvh;display:grid;grid-template-columns:minmax(320px,1.1fr) minmax(440px,.9fr);position:relative;isolation:isolate;background:#26213d url('/assets/auth/dayora-auth-bg.webp') center/cover no-repeat;color:#fff}.auth-page:before{content:'';position:absolute;inset:0;z-index:-1;background:linear-gradient(90deg,rgba(26,22,45,.34),rgba(26,22,45,.58) 48%,rgba(21,19,35,.8))}.intro{padding:clamp(32px,5vw,72px);display:flex;flex-direction:column;justify-content:space-between}.brand,.mobile-brand{display:flex;align-items:center;gap:11px;color:#fff;text-decoration:none;font-size:21px;font-weight:800;letter-spacing:-.4px}.brand-mark{display:inline-grid;place-items:center;width:38px;height:38px;border-radius:12px;background:linear-gradient(145deg,#9c8cff,#6752d4);box-shadow:0 10px 30px #3f329c66;color:#fff;font-weight:900}.intro-copy{max-width:590px}.eyebrow,.kicker{text-transform:uppercase;letter-spacing:.18em;font-size:11px;font-weight:800;color:#c8bfff}.intro h2{font-size:clamp(42px,6vw,78px);line-height:.98;letter-spacing:-.06em;margin:18px 0 22px;max-width:720px}.intro-copy p{font-size:18px;line-height:1.6;max-width:520px;color:#eeebf7}.quote{font-size:13px;color:#ddd8e9}.form-panel{display:grid;place-items:center;padding:32px}.glass-surface{width:min(440px,100%);display:grid;gap:14px;padding:clamp(28px,4vw,46px);border-radius:30px;background:rgba(255,255,255,.9);color:#292638;border:1px solid rgba(255,255,255,.58);box-shadow:0 30px 80px rgba(10,8,23,.35);backdrop-filter:blur(24px) saturate(130%)}header{margin-bottom:8px}.mobile-brand{display:none;color:#292638;margin-bottom:34px}.kicker{margin:0 0 10px;color:#7561d8}h1{font-size:clamp(28px,3vw,38px);letter-spacing:-.04em;margin:0}.subtitle{margin:9px 0 0;color:#777582}mat-form-field{width:100%}.reveal{border:0;background:transparent!important;color:#6752d4!important;padding:8px 12px;font-weight:750;cursor:pointer}.submit{min-height:52px;border-radius:15px!important;font-weight:750;font-size:15px;background:#6752d4!important;color:#fff!important}.notice{margin:0;padding:11px 14px;border-radius:12px;font-size:13px}.success{background:#e9f8ef;color:#17673a}.error{background:#fff0f0;color:#a62424}.switch{text-align:center;color:#686575;margin:8px 0 0}.switch a{color:#6752d4;font-weight:750;text-decoration:none}.switch a:hover{text-decoration:underline}
    @media(max-width:780px){.auth-page{grid-template-columns:1fr;background-position:42% center}.auth-page:before{background:rgba(22,18,38,.58)}.intro{display:none}.form-panel{padding:20px}.glass-surface{padding:28px 22px;border-radius:24px}.mobile-brand{display:flex}}@media(max-width:420px){.form-panel{padding:12px 8px}.glass-surface{padding:22px 14px;border-radius:20px}}
  `]
})
export class LoginPage {
  username=''; password=''; busy=false; error=''; registered=false; showPassword=false;
  constructor(private api:Api,private router:Router,route:ActivatedRoute){this.registered=route.snapshot.queryParamMap.get('registered')==='true';api.loadCsrf().subscribe({next:c=>api.csrf.set(c),error:()=>this.error='Unable to connect to the server.'});}
  submit(){if(this.busy)return;this.error='';this.busy=true;this.api.login(this.username,this.password).subscribe({next:()=>this.router.navigateByUrl('/today'),error:()=>{this.error='Invalid username or password.';this.busy=false;}})}
}
