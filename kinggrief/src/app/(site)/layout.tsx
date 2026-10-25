import { Header } from '@/components/header';
import { Footer } from '@/components/footer';
import { CartDrawer } from '@/components/cart-drawer';
import { BannerStrip } from '@/components/banner-strip';
import { getSettings } from '@/lib/repo';
import { bridgeStatus } from '@/lib/delivery';
import { currentUser } from '@/lib/auth';

export const dynamic = 'force-dynamic';

export default async function SiteLayout({ children }: { children: React.ReactNode }) {
  const settings = getSettings();
  const user = await currentUser();
  const bridge = bridgeStatus();
  const online = bridge.connected ? Number(settings.bridge_online || 0) : Number(settings.online_base || 0);
  const max = Number(settings.bridge_max || settings.online_peak || 0);

  return (
    <div className="relative flex min-h-dvh flex-col">
      <Header online={online} max={max} user={user ? { username: user.username } : null} />
      <main className="flex-1 pt-[68px]">
        <BannerStrip
          enabled={settings.banner_active === 'true'}
          title={settings.banner_title ?? ''}
          subtitle={settings.banner_subtitle ?? ''}
          href={settings.banner_href || '/privileges'}
          maintenance={settings.maintenance === 'true'}
          announcement={settings.announcement ?? ''}
        />
        {children}
      </main>
      <Footer
        ip={settings.server_ip ?? 'kinggrief.pickme.cloud'}
        version={settings.server_version ?? '1.16.5 — 1.21.4'}
        discord={settings.discord_url || 'https://discord.gg/kinggrief'}
        telegram={settings.telegram_url || 'https://t.me/kinggrief'}
        supportEmail={settings.support_email || 'support@kinggrief.net'}
      />
      <CartDrawer />
    </div>
  );
}
