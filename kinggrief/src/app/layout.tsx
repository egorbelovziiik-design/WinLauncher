import type { Metadata, Viewport } from 'next';
import '@fontsource-variable/manrope';
import '@fontsource-variable/unbounded';
import './globals.css';
import { Providers } from '@/components/providers';

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000'),
  title: {
    default: 'KINGGRIEF — Твоя империя грифа',
    template: '%s · KINGGRIEF',
  },
  description:
    'Привилегии, кейсы и рилики Minecraft-сервера KingGrief. Мгновенная выдача после оплаты, честные шансы, поддержка 24/7.',
  keywords: ['kinggrief', 'minecraft', 'донат', 'привилегии', 'кейсы', 'гриферский сервер', 'рилики'],
  openGraph: {
    type: 'website',
    locale: 'ru_RU',
    title: 'KINGGRIEF — Твоя империя грифа',
    description: '14 привилегий, 3 кейса, рилики и разбаны. Покупай, играй, становись легендой.',
    siteName: 'KINGGRIEF',
  },
  twitter: { card: 'summary_large_image', title: 'KINGGRIEF', description: 'Твоя империя грифа начинается здесь.' },
  icons: {
    icon: [{ url: '/favicon.svg', type: 'image/svg+xml' }],
    apple: [{ url: '/favicon.svg' }],
  },
  robots: { index: true, follow: true },
};

export const viewport: Viewport = {
  themeColor: '#080810',
  width: 'device-width',
  initialScale: 1,
  colorScheme: 'dark',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru" suppressHydrationWarning>
      <body className="min-h-dvh antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
