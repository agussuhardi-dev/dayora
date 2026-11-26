import { Injectable, signal } from '@angular/core';

/** Authenticated account identity retained for workspace selection and cache keys. */
@Injectable({providedIn:'root'}) export class LocalStore {
  readonly owner=signal<string|null>(localStorage.getItem('dayora-owner'));
  setOwner(owner:string|null){this.owner.set(owner);if(owner)localStorage.setItem('dayora-owner',owner);else localStorage.removeItem('dayora-owner');}
}
