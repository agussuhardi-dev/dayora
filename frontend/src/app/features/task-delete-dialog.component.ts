import { Component, Inject } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-task-delete-dialog',
  imports: [A11yModule, MatDialogModule, MatButtonModule],
  styles: [`
    :host{display:block;max-width:min(92vw,440px);color:var(--dayora-text)}
    h2{margin:0;overflow-wrap:anywhere}.warning{color:var(--dayora-muted);line-height:1.5}
    mat-dialog-actions{gap:8px;padding:0 24px 20px}.delete{background:#b42318!important;color:#fff!important}
    @media(max-width:480px){mat-dialog-actions{flex-direction:column-reverse;align-items:stretch}button{width:100%}}
  `],
  template: `
    <h2 mat-dialog-title>Delete task?</h2>
    <mat-dialog-content>
      <p><strong>{{data.title}}</strong></p>
      <p class="warning">This task will be permanently deleted. This action cannot be undone.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" cdkFocusInitial>Cancel</button>
      <button mat-flat-button type="button" class="delete" [mat-dialog-close]="true">Delete</button>
    </mat-dialog-actions>
  `,
})
export class TaskDeleteDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) public data: {title: string}) {}
}
