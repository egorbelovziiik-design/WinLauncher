import {invoke} from '@tauri-apps/api/core';
export const isTauri = typeof window!=='undefined' && '__TAURI_INTERNALS__' in window;
export async function call<T>(cmd:string,args:any={},fallback?:()=>Promise<T>):Promise<T>{
 if(isTauri) return invoke<T>(cmd,args);
 if(fallback) return fallback();
 throw new Error('Функция доступна в десктопной версии WinLaucher (Tauri). В браузере — предпросмотр и публичные API.');
}
