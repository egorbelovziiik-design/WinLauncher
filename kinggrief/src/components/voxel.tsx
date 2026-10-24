'use client';

import { useEffect, useRef, useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';

/* ─────────────── particles ─────────────── */
export function Particles({ density = 42, className = '' }: { density?: number; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const reduced = useReducedMotion();

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let raf = 0;
    let width = 0;
    let height = 0;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    type P = { x: number; y: number; r: number; vx: number; vy: number; hue: number; a: number };
    let points: P[] = [];

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      width = rect.width;
      height = rect.height;
      canvas.width = width * dpr;
      canvas.height = height * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      points = Array.from({ length: density }, () => ({
        x: Math.random() * width,
        y: Math.random() * height,
        r: 0.6 + Math.random() * 1.9,
        vx: (Math.random() - 0.5) * 0.16,
        vy: -0.08 - Math.random() * 0.28,
        hue: 265 + Math.random() * 50,
        a: 0.25 + Math.random() * 0.6,
      }));
    };

    const draw = () => {
      ctx.clearRect(0, 0, width, height);
      for (const p of points) {
        if (!reduced) {
          p.x += p.vx;
          p.y += p.vy;
          if (p.y < -10) p.y = height + 10;
          if (p.x < -10) p.x = width + 10;
          if (p.x > width + 10) p.x = -10;
        }
        const grad = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 4.5);
        grad.addColorStop(0, `hsla(${p.hue}, 95%, 78%, ${p.a})`);
        grad.addColorStop(1, 'hsla(270, 90%, 60%, 0)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r * 4.5, 0, Math.PI * 2);
        ctx.fill();
      }
      raf = requestAnimationFrame(draw);
    };

    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);
    if (!reduced) raf = requestAnimationFrame(draw);
    else draw();

    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, [density, reduced]);

  return <canvas ref={ref} className={`pointer-events-none ${className}`} aria-hidden="true" />;
}

/* ─────────────── CSS voxel cube ─────────────── */
export function VoxelCube({
  size = 90,
  top = '#c084fc',
  left = '#7c3aed',
  right = '#4c1d95',
  duration = 20,
  className = '',
  style,
}: {
  size?: number;
  top?: string;
  left?: string;
  right?: string;
  duration?: number;
  className?: string;
  style?: React.CSSProperties;
}) {
  const half = size / 2;
  const faces = [
    { transform: `rotateY(0deg) translateZ(${half}px)`, background: left },
    { transform: `rotateY(90deg) translateZ(${half}px)`, background: right },
    { transform: `rotateY(180deg) translateZ(${half}px)`, background: left },
    { transform: `rotateY(-90deg) translateZ(${half}px)`, background: right },
    { transform: `rotateX(90deg) translateZ(${half}px)`, background: top },
    { transform: `rotateX(-90deg) translateZ(${half}px)`, background: top },
  ];
  return (
    <div className={`scene ${className}`} style={{ width: size, height: size, ...style }} aria-hidden="true">
      <div
        className="preserve-3d relative h-full w-full"
        style={{ animation: `cubeSpin ${duration}s linear infinite`, transform: 'rotateX(-16deg) rotateY(18deg)' }}
      >
        {faces.map((f, i) => (
          <div
            key={i}
            className="cube-face"
            style={{
              ...f,
              borderColor: 'rgba(255,255,255,0.16)',
              boxShadow: 'inset 0 0 26px rgba(255,255,255,0.06)',
              backgroundColor: f.background,
              backgroundImage:
                'linear-gradient(0deg, rgba(0,0,0,0.22) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.09) 1px, transparent 1px)',
              backgroundSize: `${size / 4}px ${size / 4}px`,
            }}
          />
        ))}
      </div>
    </div>
  );
}

/* ─────────────── original pixel knight (SVG art) ─────────────── */
export function PixelKnight({ size = 210, className = '' }: { size?: number; className?: string }) {
  const px = [
    // helmet
    ['#3f3f5a', 18, 4, 12, 3], ['#2a2a40', 16, 7, 16, 3], ['#16162a', 18, 10, 12, 2],
    ['#a855f7', 20, 8, 8, 1], ['#7c3aed', 20, 9, 8, 1],
    ['#4c4c68', 15, 12, 18, 2],
    // torso / armor
    ['#5b21b6', 16, 16, 16, 10], ['#7c3aed', 18, 16, 12, 10], ['#c084fc', 20, 18, 8, 3],
    ['#facc15', 21, 22, 6, 1], ['#3b0764', 16, 26, 16, 2],
    // shoulders
    ['#8b5cf6', 12, 16, 4, 5], ['#8b5cf6', 32, 16, 4, 5],
    ['#c4b5fd', 13, 16, 2, 2], ['#c4b5fd', 33, 16, 2, 2],
    // arms
    ['#6d28d9', 12, 21, 4, 8], ['#6d28d9', 32, 21, 4, 8], ['#d8b4fe', 12, 29, 4, 2], ['#d8b4fe', 32, 29, 4, 2],
    // legs
    ['#312e5f', 18, 28, 5, 9], ['#312e5f', 25, 28, 5, 9], ['#1e1b3a', 17, 37, 6, 2], ['#1e1b3a', 25, 37, 6, 2],
    // cape
    ['#7c3aed', 35, 18, 3, 14], ['#a855f7', 37, 21, 2, 10],
    // sword
    ['#94a3b8', 8, 12, 2, 14], ['#cbd5e1', 8, 12, 2, 5], ['#facc15', 6, 24, 6, 2], ['#fde68a', 9, 26, 1, 4],
    // highlights
    ['#e9d5ff', 17, 17, 1, 7], ['#ffffff', 21, 19, 2, 1],
  ] as const;
  return (
    <svg
      width={size}
      height={(size * 48) / 48}
      viewBox="0 0 48 42"
      className={`pixel-edge ${className}`}
      aria-hidden="true"
      style={{ filter: 'drop-shadow(0 0 26px rgba(168,85,247,0.55))' }}
    >
      {px.map(([fill, x, y, w, h], i) => (
        <rect key={i} x={x} y={y} width={w} height={h} fill={fill} />
      ))}
    </svg>
  );
}

/* ─────────────── 3D crown ─────────────── */
export function CrownLarge({ size = 240, className = '', rainbow = false }: { size?: number; className?: string; rainbow?: boolean }) {
  return (
    <svg width={size} height={size * 0.72} viewBox="0 0 200 144" className={className} aria-hidden="true">
      <defs>
        <linearGradient id="crownFace" x1="20" y1="20" x2="180" y2="120" gradientUnits="userSpaceOnUse">
          <stop stopColor="#fef3c7" /><stop offset="0.35" stopColor="#facc15" />
          <stop offset="0.65" stopColor="#f59e0b" /><stop offset="1" stopColor="#b45309" />
        </linearGradient>
        <linearGradient id="crownSide" x1="0" y1="0" x2="200" y2="140" gradientUnits="userSpaceOnUse">
          <stop stopColor="#fbbf24" /><stop offset="1" stopColor="#92400e" />
        </linearGradient>
        {rainbow && (
          <linearGradient id="crownRainbow" x1="0" y1="0" x2="200" y2="144" gradientUnits="userSpaceOnUse">
            <stop stopColor="#f472b6" /><stop offset="0.3" stopColor="#a855f7" />
            <stop offset="0.6" stopColor="#22d3ee" /><stop offset="1" stopColor="#facc15" />
          </linearGradient>
        )}
        <filter id="crownGlow" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="6" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <ellipse cx="100" cy="132" rx="64" ry="8" fill="#facc15" opacity="0.18" />
      <g filter="url(#crownGlow)">
        <path d="M32 108 26 38l38 26 36-44 36 44 38-26-6 70Z" fill="url(#crownFace)" stroke="#fde68a" strokeWidth="1.5" />
        <path d="M32 108 26 38l38 26 36-44v14L64 72 38 54l4 48Z" fill="url(#crownSide)" opacity="0.55" />
        <path d="M46 112h108l-6 20a6 6 0 0 1-6 5H58a6 6 0 0 1-6-5Z" fill={rainbow ? 'url(#crownRainbow)' : 'url(#crownFace)'} stroke="#fde68a" strokeWidth="1.5" />
        <circle cx="100" cy="122" r="7" fill="#7c3aed" stroke="#fde68a" strokeWidth="1.5" />
        <circle cx="66" cy="122" r="5" fill="#22d3ee" stroke="#fde68a" strokeWidth="1.2" />
        <circle cx="134" cy="122" r="5" fill="#ec4899" stroke="#fde68a" strokeWidth="1.2" />
        <circle cx="26" cy="38" r="7" fill={rainbow ? 'url(#crownRainbow)' : '#fde68a'} />
        <circle cx="174" cy="38" r="7" fill={rainbow ? 'url(#crownRainbow)' : '#fde68a'} />
        <circle cx="100" cy="20" r="8" fill={rainbow ? 'url(#crownRainbow)' : '#fff7cc'} />
      </g>
    </svg>
  );
}

/* ─────────────── 3D chest ─────────────── */
export function Chest3D({ color = '#A855F7', size = 132, className = '' }: { color?: string; size?: number; className?: string }) {
  return (
    <div className={`relative ${className}`} style={{ width: size, height: size * 0.92 }} aria-hidden="true">
      <div className="absolute left-1/2 top-1/2 h-[70%] w-[92%] -translate-x-1/2 -translate-y-1/2 rounded-full blur-2xl" style={{ background: color, opacity: 0.28 }} />
      <svg viewBox="0 0 120 112" width={size} height={size * 0.92} className="relative">
        <defs>
          <linearGradient id={`lid-${color}`} x1="0" y1="0" x2="120" y2="60" gradientUnits="userSpaceOnUse">
            <stop stopColor={color} stopOpacity="0.95" /><stop offset="1" stopColor="#1a1024" />
          </linearGradient>
          <linearGradient id={`body-${color}`} x1="0" y1="40" x2="120" y2="112" gradientUnits="userSpaceOnUse">
            <stop stopColor="#2a1a3a" /><stop offset="1" stopColor="#0d0a14" />
          </linearGradient>
        </defs>
        <g>
          <path d="M18 42h84a8 8 0 0 0 8-8V30a12 12 0 0 0-12-12H22A12 12 0 0 0 10 30v4a8 8 0 0 0 8 8Z" fill={`url(#lid-${color})`} stroke={color} strokeWidth="1.4" />
          <path d="M14 46h92v48a10 10 0 0 1-10 10H24a10 10 0 0 1-10-10Z" fill={`url(#body-${color})`} stroke={color} strokeWidth="1.4" />
          <path d="M14 46h92v10H14Z" fill={color} opacity="0.3" />
          <rect x="52" y="42" width="16" height="24" rx="3" fill={color} />
          <circle cx="60" cy="52" r="3" fill="#0d0a14" />
          <path d="M60 55v6" stroke="#0d0a14" strokeWidth="2.4" strokeLinecap="round" />
          <path d="M18 34h84M18 30h84" stroke="#fff" strokeOpacity="0.22" strokeWidth="1" />
          <path d="M22 100h76" stroke={color} strokeOpacity="0.35" strokeWidth="1.4" />
          <circle cx="30" cy="76" r="1.6" fill="#fff" opacity="0.5" />
          <circle cx="92" cy="68" r="1.2" fill="#fff" opacity="0.4" />
          <circle cx="74" cy="92" r="1.4" fill="#fff" opacity="0.35" />
        </g>
      </svg>
    </div>
  );
}

/* ─────────────── relic coin ─────────────── */
export function Coin({ size = 64, className = '', style }: { size?: number; className?: string; style?: React.CSSProperties }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" className={className} style={style} aria-hidden="true">
      <defs>
        <linearGradient id="coinFace" x1="8" y1="8" x2="56" y2="56" gradientUnits="userSpaceOnUse">
          <stop stopColor="#fef9c3" /><stop offset="0.45" stopColor="#facc15" /><stop offset="1" stopColor="#d97706" />
        </linearGradient>
      </defs>
      <circle cx="32" cy="32" r="27" fill="url(#coinFace)" stroke="#fde68a" strokeWidth="2" />
      <circle cx="32" cy="32" r="21" fill="none" stroke="#a16207" strokeOpacity="0.55" strokeWidth="1.6" />
      <path d="M32 16 40 32l-8 16-8-16Z" fill="#a16207" opacity="0.75" />
      <path d="M32 20 37.5 32 32 44l-5.5-12Z" fill="#fef9c3" opacity="0.8" />
      <path d="M14 20a22 22 0 0 1 14-9" stroke="#fff" strokeOpacity="0.7" strokeWidth="3" strokeLinecap="round" fill="none" />
    </svg>
  );
}

/* ─────────────── hero scene ─────────────── */
export function HeroScene() {
  const reduced = useReducedMotion();
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const boxRef = useRef<HTMLDivElement>(null);

  const onMove = (e: React.MouseEvent) => {
    if (reduced || !boxRef.current) return;
    const rect = boxRef.current.getBoundingClientRect();
    const px = (e.clientX - rect.left) / rect.width - 0.5;
    const py = (e.clientY - rect.top) / rect.height - 0.5;
    setTilt({ x: py * -8, y: px * 12 });
  };

  return (
    <div
      ref={boxRef}
      onMouseMove={onMove}
      onMouseLeave={() => setTilt({ x: 0, y: 0 })}
      className="scene relative mx-auto h-[380px] w-full max-w-[520px] sm:h-[460px] lg:h-[520px]"
    >
      {/* glow floor */}
      <div className="absolute inset-x-6 bottom-10 h-24 rounded-[50%] bg-violet/30 blur-3xl" />
      <div className="absolute inset-x-2 bottom-16 h-40 rounded-[50%] bg-gradient-to-r from-pink/10 via-neon/25 to-blue/10 blur-2xl" />
      <div
        className="absolute bottom-[4.5rem] left-1/2 h-40 w-[86%] -translate-x-1/2 opacity-40"
        style={{
          backgroundImage:
            'linear-gradient(rgba(168,85,247,0.4) 1px, transparent 1px), linear-gradient(90deg, rgba(168,85,247,0.4) 1px, transparent 1px)',
          backgroundSize: '30px 30px',
          transform: 'translateX(-50%) perspective(320px) rotateX(66deg)',
          maskImage: 'radial-gradient(ellipse at center, #000 10%, transparent 70%)',
          WebkitMaskImage: 'radial-gradient(ellipse at center, #000 10%, transparent 70%)',
        }}
      />

      <Particles density={34} className="absolute inset-0 h-full w-full" />

      <motion.div
        className="relative flex h-full items-center justify-center"
        animate={{ rotateX: tilt.x, rotateY: tilt.y }}
        transition={{ type: 'spring', stiffness: 90, damping: 18 }}
        style={{ transformStyle: 'preserve-3d' }}
      >
        {/* back ring */}
        <div className="absolute h-[300px] w-[300px] rounded-full border border-neon/25 sm:h-[360px] sm:w-[360px]" style={{ animation: 'spin 26s linear infinite' }}>
          <span className="absolute -top-1 left-1/2 h-2 w-2 -translate-x-1/2 rounded-full bg-neon shadow-[0_0_18px_4px_rgba(168,85,247,0.8)]" />
        </div>
        <div className="absolute h-[230px] w-[230px] rounded-full border border-dashed border-pink/20 sm:h-[280px] sm:w-[280px]" style={{ animation: 'spin 34s linear infinite reverse' }} />

        {/* cubes */}
        <motion.div className="absolute left-2 top-16 opacity-90 sm:left-6" animate={reduced ? {} : { y: [0, -14, 0] }} transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut' }}>
          <VoxelCube size={64} top="#c084fc" left="#7c3aed" right="#4c1d95" duration={16} />
        </motion.div>
        <motion.div className="absolute right-0 top-8 hidden sm:block" animate={reduced ? {} : { y: [0, 18, 0] }} transition={{ duration: 9, repeat: Infinity, ease: 'easeInOut' }}>
          <VoxelCube size={52} top="#f0abfc" left="#ec4899" right="#9d174d" duration={19} />
        </motion.div>
        <motion.div className="absolute bottom-24 right-6 opacity-80" animate={reduced ? {} : { y: [0, -10, 0] }} transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}>
          <VoxelCube size={42} top="#93c5fd" left="#3b82f6" right="#1e3a8a" duration={23} />
        </motion.div>

        {/* knight + crown */}
        <div className="relative flex flex-col items-center">
          <motion.div
            className="relative z-20 -mb-2"
            animate={reduced ? {} : { y: [0, -12, 0], rotate: [-2, 2, -2] }}
            transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
          >
            <div className="absolute inset-0 -z-10 blur-2xl" style={{ background: 'radial-gradient(circle, rgba(250,204,21,0.45), transparent 65%)' }} />
            <CrownLarge size={228} />
          </motion.div>
          <motion.div
            className="relative z-10"
            animate={reduced ? {} : { y: [0, 6, 0] }}
            transition={{ duration: 5.5, repeat: Infinity, ease: 'easeInOut' }}
          >
            <PixelKnight size={196} />
          </motion.div>
          <div className="relative z-0 h-3 w-40 rounded-[50%] bg-neon/40 blur-md" />
        </div>

        {/* floating coins */}
        <motion.div className="absolute bottom-16 left-8 sm:left-14" animate={reduced ? {} : { y: [0, -18, 0], rotate: [0, 12, 0] }} transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut' }}>
          <Coin size={44} />
        </motion.div>
        <motion.div className="absolute bottom-28 right-10" animate={reduced ? {} : { y: [0, 14, 0], rotate: [0, -10, 0] }} transition={{ duration: 6.5, repeat: Infinity, ease: 'easeInOut' }}>
          <Coin size={30} />
        </motion.div>
      </motion.div>

      {/* floating chips */}
      <motion.div
        className="glass absolute right-1 top-1/2 hidden rounded-xl px-3 py-2 text-[0.7rem] font-bold sm:block"
        animate={reduced ? {} : { y: [0, -8, 0] }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
      >
        <span className="block font-mono text-[0.55rem] uppercase tracking-widest text-neon">Привилегия</span>
        <span className="gold-text font-display text-sm">D.OWNER</span>
      </motion.div>
      <motion.div
        className="glass absolute bottom-8 left-0 hidden rounded-xl px-3 py-2 text-[0.7rem] font-bold sm:block"
        animate={reduced ? {} : { y: [0, 10, 0] }}
        transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut' }}
      >
        <span className="block font-mono text-[0.55rem] uppercase tracking-widest text-emerald">Онлайн</span>
        <span className="stat-num text-sm text-white">24/7</span>
      </motion.div>
    </div>
  );
}
