import Link from 'next/link';
import { ShieldAlert, Lock } from 'lucide-react';
import { currentAdmin } from '@/lib/auth';
import { LpEditorClient } from './lp-editor-client';

export const dynamic = 'force-dynamic';

export default async function LpEditorPage() {
  const admin = await currentAdmin();
  if (!admin || admin.role !== 'owner') {
    return (
      <main className="min-h-screen bg-[#07080f] flex items-center justify-center px-6 py-16 text-white">
        <div className="max-w-md w-full rounded-2xl border border-rose-500/30 bg-[#111322] p-8 text-center shadow-2xl">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-rose-500/15 border border-rose-500/35 text-rose-400">
            <Lock size={28} />
          </div>
          <div className="inline-flex items-center gap-1.5 rounded-full bg-rose-500/15 px-3 py-1 text-xs font-bold uppercase tracking-wider text-rose-300 mb-3">
            <ShieldAlert size={13} /> STRICTLY OWNER ONLY
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight text-white mb-2">
            Доступ закрыт
          </h1>
          <p className="text-sm text-white/65 leading-relaxed mb-6">
            Веб-редактор <span className="font-semibold text-white">KINGGRIEF LP Editor</span> доступен
            строго только владельцу сервера с рангом <span className="font-bold text-rose-400">OWNER</span>.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link
              href="/admin/login"
              className="rounded-xl bg-rose-600 hover:bg-rose-500 px-5 py-2.5 text-sm font-bold text-white transition"
            >
              Войти как OWNER
            </Link>
            <Link
              href="/"
              className="rounded-xl border border-white/15 bg-white/5 hover:bg-white/10 px-5 py-2.5 text-sm font-semibold text-white/80 transition"
            >
              На главную
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return <LpEditorClient />;
}
