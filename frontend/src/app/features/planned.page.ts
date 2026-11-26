import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  template: '<main><h1>Planning space</h1><p>Tasks and calendar are not connected to the backend yet. No data is displayed.</p><a routerLink="/today">Back to overview</a></main>',
  styles: ['main{max-width:650px;margin:60px auto;padding:24px;color:var(--dayora-text)}a{color:var(--dayora-primary)}'],
})
export class PlannedPage {}
