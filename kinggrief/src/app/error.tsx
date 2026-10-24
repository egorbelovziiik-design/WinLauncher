'use client';

import Link from 'next/link';
import { AlertTriangle, RefreshCw } from 'lucide-react';

export default function ErrorPage({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 text-center">
      <span className="grid h-14 w-14 place-items-center rounded-2xl bg-danger/12 text-danger">
        <AlertTriangle size={26} />
      </span>
      <p className="mt-4 font-mono text-[0.62rem] uppercase tracking-[0.3em] text-danger">server error</p>
      <h1 className="mt-2 display text-3xl text-white">Что-то сломалось</h1>
      <p className="mt-2 max-w-sm text-[0.82rem] leading-relaxed text-white/40">
        Мы записали ошибку. Попробуйте обновить страницу — если не помогает, напишите в поддержку.
      </p>
      <div className="mt-6 flex gap-2">
        <button onClick={reset} className="btn btn-primary"><RefreshCw size={14} /> Повторить</button>
        <Link href="/" className="btn btn-ghost">На главную</Link>
      </div>
    </div>
  );
}
