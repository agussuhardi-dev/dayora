import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { SettingsService } from '../settings.service';
import { FocusPipService } from './focus/focus-pip.service';
import { PushService } from '../push.service';

@Component({
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatIconModule],
  template: `
    <main class="settings-page">
      <header class="heading">
        <h1>Settings</h1>
      </header>

      <form (ngSubmit)="save()">
        <div class="cards">
          @for (field of fields; track field.key) {
            <section class="duration surface" [class.focus-card]="field.key === 'focus'">
              <div class="card-top">
                <span class="icon-tile"><mat-icon aria-hidden="true">{{ field.icon }}</mat-icon></span>
              </div>
              <label [for]="field.key">{{ field.label }}</label>
              <div class="stepper">
                <button mat-icon-button type="button" [attr.aria-label]="'Decrease ' + field.label" [disabled]="this[field.key] <= 1" (click)="adjust(field.key, -1, field.max)"><mat-icon>remove</mat-icon></button>
                <input [id]="field.key" [name]="field.key" type="number" min="1" [max]="field.max" step="1" required [(ngModel)]="this[field.key]" [attr.aria-describedby]="field.key + '-range'" />
                <button mat-icon-button type="button" [attr.aria-label]="'Increase ' + field.label" [disabled]="this[field.key] >= field.max" (click)="adjust(field.key, 1, field.max)"><mat-icon>add</mat-icon></button>
              </div>
              <p class="unit muted" [id]="field.key + '-range'">{{ field.unit }} · 1–{{ field.max }}</p>
            </section>
          }
        </div>

        <div class="options-grid">
          <section class="option-card surface">
            <div class="option-header">
              <span class="icon-tile"><mat-icon aria-hidden="true">volume_up</mat-icon></span>
              <h2>Sound</h2>
            </div>
            <div class="option-list">
              <label class="toggle-row">
                <div class="toggle-info">
                  <strong>Sound effects</strong>
                  <span class="muted">Play sound on session starts, pauses, and completions.</span>
                </div>
                <input type="checkbox" [(ngModel)]="soundEnabled" name="soundEnabled" (change)="onSoundChange()" />
              </label>
              <label class="toggle-row" [class.disabled]="!soundEnabled">
                <div class="toggle-info">
                  <strong>Break ticking</strong>
                  <span class="muted">A quiet tick during short and long breaks.</span>
                </div>
                <input type="checkbox" [(ngModel)]="tickingEnabled" name="tickingEnabled" [disabled]="!soundEnabled" (change)="submitted = false" />
              </label>
            </div>
          </section>

          <section class="option-card surface">
            <div class="option-header">
              <span class="icon-tile"><mat-icon aria-hidden="true">notifications</mat-icon></span>
              <h2>Notifications</h2>
            </div>
            <div class="option-list">
              <div class="toggle-row">
                <div class="toggle-info">
                  <strong>Desktop notifications</strong>
                  <span class="muted">Receive an alert when a focus or break session finishes.</span>
                </div>
                <div class="notif-action">
                  @if (notifState === 'granted') {
                    <span class="badge-granted"><mat-icon aria-hidden="true">check_circle</mat-icon> Enabled</span>
                    <button mat-button type="button" class="notif-btn" (click)="testNotification()">Test</button>
                  } @else if (notifState === 'denied') {
                    <span class="badge-denied"><mat-icon aria-hidden="true">block</mat-icon> Blocked</span>
                  } @else if (notifState === 'unsupported') {
                    <span class="muted">Unsupported</span>
                  } @else {
                    <button mat-flat-button type="button" class="notif-btn" (click)="requestNotificationPermission()">Enable</button>
                  }
                </div>
              </div>
              <p class="muted" role="status">{{ push.message() }}</p>
              <button mat-button type="button" [disabled]="push.busy()" (click)="requestNotificationPermission()">{{ push.busy() ? 'Checking…' : 'Enable / retry web push' }}</button>
            </div>
          </section>
        </div>

        @if (s.error()) {
          <div class="error surface" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon><p>{{ s.error() }}</p><button mat-button type="button" (click)="s.retry()" [disabled]="s.saving()">Retry</button></div>
        }
        <footer class="save-bar">
          <p class="muted" role="status" aria-live="polite">{{ s.saving() ? 'Saving…' : s.error() ? 'Changes not saved.' : submitted ? 'Settings saved.' : '' }}</p>
          <div class="save-actions"><button mat-button type="button" (click)="resetDefaults()" [disabled]="s.saving()"><mat-icon aria-hidden="true">restart_alt</mat-icon>Reset defaults</button><button mat-flat-button type="submit" [disabled]="!valid() || s.saving()"><mat-icon aria-hidden="true">check</mat-icon>{{ s.saving() ? 'Saving…' : 'Save changes' }}</button></div>
        </footer>
      </form>
    </main>
  `,
  styles: [`
    :host{display:block}.settings-page{max-width:1080px;margin:auto;padding:34px 8px 54px}.heading{margin:0 4px 28px}.heading h1{margin:0;font-size:clamp(2rem,4vw,2.8rem);letter-spacing:-.04em}.muted{color:var(--dayora-muted);line-height:1.6}.surface{background:var(--dayora-glass);border:1px solid var(--dayora-glass-border);border-radius:24px;box-shadow:0 12px 32px rgb(64 57 95 / .05);backdrop-filter:blur(16px)}.cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin-bottom:24px}.duration{padding:22px 16px}.focus-card{border-color:color-mix(in srgb,var(--dayora-primary) 38%,var(--dayora-glass-border))}.card-top{display:flex;align-items:center;margin-bottom:18px}.icon-tile{display:grid;place-items:center;width:42px;height:42px;border-radius:13px;color:var(--dayora-primary-dark);background:color-mix(in srgb,var(--dayora-primary) 12%,transparent)}.duration label{font-size:.95rem;font-weight:700;display:block;margin-bottom:14px;min-height:2.4em}.stepper{display:flex;align-items:center;gap:2px;background:var(--dayora-glass-strong);border:1px solid var(--dayora-glass-border);border-radius:16px;padding:4px}.stepper button{flex-shrink:0;width:44px;height:44px;padding:10px}.stepper input{width:100%;min-width:0;border:0;background:transparent;color:var(--dayora-text);text-align:center;font:inherit;font-size:1.7rem;font-weight:650;appearance:textfield;-moz-appearance:textfield}.stepper input::-webkit-inner-spin-button,.stepper input::-webkit-outer-spin-button{-webkit-appearance:none;margin:0}.stepper input:focus-visible{outline:2px solid var(--dayora-primary);outline-offset:2px;border-radius:4px}.unit{text-align:center;font-size:.7rem;margin:10px 0 0}.options-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-bottom:24px}.option-card{padding:22px 20px}.option-header{display:flex;align-items:center;gap:14px;margin-bottom:18px}.option-header h2{margin:0;font-size:1.15rem;letter-spacing:-.02em}.option-list{display:grid;gap:12px}.toggle-row{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 16px;border:1px solid var(--dayora-glass-border);border-radius:16px;background:var(--dayora-glass-strong);cursor:pointer}.toggle-row.disabled{opacity:.5;cursor:not-allowed}.toggle-info{display:grid;gap:3px}.toggle-info strong{font-size:.9rem}.toggle-info .muted{font-size:.78rem;line-height:1.4}.toggle-row input[type="checkbox"]{width:20px;height:20px;flex:none;accent-color:var(--dayora-primary);cursor:pointer}.toggle-row.disabled input[type="checkbox"]{cursor:not-allowed}.notif-action{display:flex;align-items:center;gap:8px;flex-shrink:0}.badge-granted{display:inline-flex;align-items:center;gap:5px;font-size:.8rem;font-weight:700;color:#2e7d32}.badge-granted mat-icon{font-size:18px;width:18px;height:18px}.badge-denied{display:inline-flex;align-items:center;gap:5px;font-size:.8rem;font-weight:700;color:var(--mat-sys-error)}.badge-denied mat-icon{font-size:18px;width:18px;height:18px}.notif-btn{min-height:36px;padding:0 12px;font-size:.82rem}.save-bar{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:22px 4px}.save-bar p{font-size:.82rem;margin:0}.save-bar button{min-height:46px;flex-shrink:0}.error{display:flex;align-items:center;gap:12px;padding:12px 18px;margin-top:20px;color:var(--mat-sys-error)}.error p{flex:1;font-size:.85rem}.error mat-icon{flex-shrink:0}@media(max-width:1000px){.cards{grid-template-columns:repeat(2,minmax(0,1fr))}.options-grid{grid-template-columns:1fr}}@media(max-width:540px){.settings-page{padding:22px 0 36px}.cards{grid-template-columns:1fr;gap:12px}.duration{padding:20px}.duration label{min-height:0}.toggle-row{flex-direction:column;align-items:flex-start;gap:10px}.save-bar{align-items:stretch;flex-direction:column;gap:14px}.error{flex-wrap:wrap}}
  `],
})
export class SettingsPage {
  readonly pip = inject(FocusPipService);
  readonly push = inject(PushService);
  focus = 25; short = 5; long = 15; cycles = 4;
  soundEnabled = true; tickingEnabled = true;
  submitted = false;
  notifState: string = typeof Notification !== 'undefined' ? Notification.permission : 'unsupported';

  readonly fields = [
    { key: 'focus', label: 'Focus time', icon: 'center_focus_strong', unit: 'minutes', max: 1440 },
    { key: 'short', label: 'Short break', icon: 'free_breakfast', unit: 'minutes', max: 1440 },
    { key: 'long', label: 'Long break', icon: 'weekend', unit: 'minutes', max: 1440 },
    { key: 'cycles', label: 'Cycles before long break', icon: 'repeat', unit: 'cycles', max: 12 },
  ] as const;

  constructor(readonly s: SettingsService) {
    void this.push.refresh();
    effect(() => {
      const p = s.settings()?.pomodoro;
      const snd = s.settings()?.sound;
      if (p && !this.submitted) {
        this.focus = p.focusMinutes;
        this.short = p.shortBreakMinutes;
        this.long = p.longBreakMinutes;
        this.cycles = p.cyclesBeforeLongBreak ?? 4;
      }
      if (snd && !this.submitted) {
        this.soundEnabled = snd.enabled;
        this.tickingEnabled = snd.breakTicking;
      }
    });
  }

  adjust(key: 'focus' | 'short' | 'long' | 'cycles', delta: number, max: number) {
    this[key] = Math.max(1, Math.min(max, (Number(this[key]) || 1) + delta));
    this.submitted = false;
  }

  onSoundChange() {
    if (!this.soundEnabled) this.tickingEnabled = false;
    this.submitted = false;
  }

  async requestNotificationPermission() {
    await this.push.enable();
    this.notifState = typeof Notification === 'undefined' ? 'unsupported' : Notification.permission;
    if(this.notifState==='granted')this.pip.enableNotifications();
  }

  testNotification() {
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      void this.push.test();
    }
  }

  valid() {
    return [this.focus, this.short, this.long].every(n => Number.isInteger(n) && n > 0 && n <= 1440)
      && Number.isInteger(this.cycles) && this.cycles >= 1 && this.cycles <= 12;
  }

  resetDefaults() {
    this.focus = 25; this.short = 5; this.long = 15; this.cycles = 4;
    this.soundEnabled = true; this.tickingEnabled = true;
    this.submitted = false;
  }

  save() {
    if (!this.valid() || this.s.saving()) return;
    this.s.update('pomodoro', { focusMinutes: this.focus, shortBreakMinutes: this.short, longBreakMinutes: this.long, cyclesBeforeLongBreak: this.cycles });
    this.s.update('sound', { enabled: this.soundEnabled, breakTicking: this.tickingEnabled });
    this.submitted = true;
  }
}
