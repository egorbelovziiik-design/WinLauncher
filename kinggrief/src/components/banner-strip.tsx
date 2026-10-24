'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AlertTriangle, ArrowRight, Sparkles, X } from 'lucide-react';

export function BannerStrip({
  enabled,
  title,
  subtitle,
  href,
  maintenance,
  announcement,
}: {
  enabled: boolean;
  title: string;
  subtitle: string;
  href: string;
  maintenance: boolean;
  announcement: string;
}) {
  const [closed, setClosed] = useState(true);
  const key = `kg_banner_${title}_${maintenance}`;

  useEffect(() => {
    setClosed(window.sessionStorage.getItem(key) === '1');
  }, [key]);

  if (closed) return null;
  if (!maintenance && !enabled && !announcement) return null;

  const dismiss = () => {
    window.sessionStorage.setItem(key, '1');
    setClosed(true);
  };

  const style = maintenance
    ? 'border-amber/30 bg-gradient-to-r from-[#2a1f08] via-[#221a0c] to-[#1a1408]'
    : 'border-neon/20 bg-gradient-to-r from-[#1a1233] via-[#241540] to-[#1a1233]';

  return (
    <div className={`border-b ${style}`}>
      <div className="container-x flex items-center gap-3 py-2">
        {maintenance ? <AlertTriangle size={13} className="shrink-0 text-amber" /> : <Sparkles size={13} className="shrink-0 text-neon" />}
        <p className="min-w-0 flex-1 truncate text-[0.72rem]">
          <span className="font-bold text-white">{maintenance ? 'Технические работы' : title}</span>
          <span className="ml-2 hidden text-white/45 sm:inline">{maintenance ? 'Возможны кратковременные перерывы в выдаче.' : subtitle || announcement}</span>
        </p>
        {href && !maintenance && (
          <Link href={href} className="hidden shrink-0 items-center gap-1 font-mono text-[0.6rem] font-bold uppercase tracking-widest text-neon hover:text-white sm:inline-flex">
            смотреть <ArrowRight size={11} />
          </Link>
        )}
        <button onClick={dismiss} className="shrink-0 text-white/30 transition hover:text-white" aria-label="Скрыть баннер">
          <X size={13} />
        </button>
      </div>
    </div>
  );
}
