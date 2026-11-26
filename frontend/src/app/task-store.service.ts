import { effect, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api, Task } from './api';
import { LocalStore } from './local-store';
import { RealtimeService } from './realtime.service';

@Injectable({providedIn:'root'}) export class TaskStore {
  readonly tasks=signal<Task[]>([]); readonly status=signal('');
  private generation=0; private account='';
  constructor(private api:Api,private owner:LocalStore,realtime:RealtimeService){effect(()=>{this.owner.owner();this.current();});realtime.invalidated.subscribe(()=>void this.load());addEventListener('online',()=>void this.load());}
  private current(){const account=this.owner.owner()||'';if(account!==this.account){this.account=account;this.generation++;this.tasks.set([]);this.status.set('');}return account;}
  async load(){
    const account=this.current(),generation=++this.generation;if(!account)return;
    if(!this.api.online()){this.status.set('Offline · task changes unavailable');return;}
    try {
      const tasks:Task[]=[];let page=0,total=0;
      do {const result=await firstValueFrom(this.api.tasks(page++));tasks.push(...result.content);total=result.totalElements;}
      while(tasks.length<total&&generation===this.generation&&account===this.owner.owner());
      if(generation===this.generation&&account===this.owner.owner()){this.tasks.set(tasks);this.status.set('Tasks loaded.');}
    } catch {if(generation===this.generation&&account===this.owner.owner())this.status.set('Tasks could not be loaded. Refresh to retry.');}
  }
  private begin(){const account=this.current();if(!account)throw new Error('No authenticated account.');this.generation++;return account;}
  private valid(account:string){return account===this.account&&account===this.owner.owner();}
  private failed(error:unknown):never{if((error as {status?:number}).status===409)this.status.set('This task changed elsewhere. Reload before editing.');else this.status.set('Task could not be saved. Try again.');throw error;}
  async create(title:string,workspaceId?:string){const account=this.begin();try{const task=await firstValueFrom(this.api.createTask({title,workspaceId,priority:'NONE'}));if(!this.valid(account))throw new Error('Your session changed. Reload before adding a task.');this.generation++;this.tasks.update(v=>v.some(t=>t.id===task.id)?v:[task,...v]);this.status.set('Task added.');return task;}catch(e){return this.failed(e);}}
  async update(task:Task){const account=this.begin();try{const saved=await firstValueFrom(this.api.updateTask(task));if(this.valid(account)){this.generation++;this.tasks.update(v=>v.map(t=>t.id===saved.id?saved:t));}return saved;}catch(e){return this.failed(e);}}
  async remove(task:Task){const account=this.begin();try{await firstValueFrom(this.api.deleteTask(task));if(this.valid(account)){this.generation++;this.tasks.update(v=>v.filter(t=>t.id!==task.id));}}catch(e){this.failed(e);}}
}
