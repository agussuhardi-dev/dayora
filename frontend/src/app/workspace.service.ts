import { Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api, Workspace } from './api';
import { LocalStore } from './local-store';

@Injectable({providedIn:'root'}) export class WorkspaceService {
  readonly workspaces=signal<Workspace[]>([]); readonly selectedId=signal(''); readonly error=signal(''); private owner=''; private opening=0;
  constructor(public api:Api,private store:LocalStore){}
  accountId(){return this.store.owner()||undefined;}
  async open(){
    const opening=++this.opening, owner=this.store.owner(); this.owner=owner||''; this.workspaces.set([]); this.selectedId.set(''); this.error.set('');
    if(owner){
      try { const cached=JSON.parse(localStorage.getItem(`dayora-workspaces:${owner}`)||'[]'); if(Array.isArray(cached))this.workspaces.set(cached.filter(w=>typeof w?.id==='string'&&typeof w?.name==='string')); } catch {}
      this.restore();
    }
    if(!this.api.online())return;
    try {
      const {id}=await firstValueFrom(this.api.me());
      if(opening!==this.opening)return;
      // A browser can retain a previous account's ID after logout or a database reset.
      // The authenticated server response, not that cache, is authoritative.
      this.store.setOwner(id); this.owner=id;
      const workspaces=await firstValueFrom(this.api.workspaces());
      if(opening!==this.opening||this.store.owner()!==id)return;
      this.workspaces.set(workspaces); localStorage.setItem(`dayora-workspaces:${id}`,JSON.stringify(workspaces)); this.restore();
    } catch { if(opening===this.opening)this.error.set('Workspaces unavailable. Cached selection retained.'); }
  }
  private restore(){const saved=localStorage.getItem(`dayora-workspace:${this.owner}`);this.select(this.workspaces().some(w=>w.id===saved)?saved!:this.workspaces()[0]?.id||'');}
  select(id:string){if(id&&!this.workspaces().some(w=>w.id===id))return;this.selectedId.set(id);if(this.owner&&this.store.owner()===this.owner&&id)localStorage.setItem(`dayora-workspace:${this.owner}`,id);}
  matches(entity:{workspaceId?:string}){return (entity.workspaceId||this.workspaces()[0]?.id||'')===this.selectedId();}
  async create(name:string){
    name=name.trim(); if(!name||name.length>120||!this.api.online()||!this.owner||this.store.owner()!==this.owner)return;
    const owner=this.owner;
    try {const w=await firstValueFrom(this.api.createWorkspace({name}));if(this.store.owner()!==owner)return;this.workspaces.update(v=>[...v,w]);localStorage.setItem(`dayora-workspaces:${owner}`,JSON.stringify(this.workspaces()));this.select(w.id);this.error.set('');}catch{this.error.set('Workspace could not be created.');}
  }
}
