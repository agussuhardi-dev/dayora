import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RegisterPage } from './register.page';
import { authInterceptor } from './api';

describe('RegisterPage',()=>{
  let http:HttpTestingController;
  beforeEach(async()=>{await TestBed.configureTestingModule({imports:[RegisterPage],providers:[provideRouter([]),provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]}).compileComponents();http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  it('validates UTF-8 byte length and matching passwords',()=>{const page=TestBed.createComponent(RegisterPage).componentInstance;page.username='user';page.password='short';page.confirm='short';page.submit();expect(page.error).toContain('12–72');page.password='valid-password';page.confirm='different-pass';page.submit();expect(page.error).toBe('Passwords do not match.');});
  it('registers then redirects to login with notice',()=>{const router=TestBed.inject(Router);const navigate=vi.spyOn(router,'navigate').mockResolvedValue(true);const page=TestBed.createComponent(RegisterPage).componentInstance;page.username=' user ';page.password=page.confirm='valid-password';page.timezone='America/New_York';page.submit();page.submit();const csrf=http.expectOne('/api/auth/csrf');expect(csrf.request.method).toBe('GET');csrf.flush({headerName:'X-XSRF-TOKEN',token:'test-csrf'});const req=http.expectOne('/api/auth/register');expect(req.request.method).toBe('POST');expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');expect(req.request.body).toEqual({username:'user',password:'valid-password',timezone:'America/New_York'});req.flush({});expect(navigate).toHaveBeenCalledWith(['/login'],{queryParams:{registered:'true'}});});
});
