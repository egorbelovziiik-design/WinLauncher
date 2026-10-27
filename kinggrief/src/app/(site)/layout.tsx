import { Header } from '@/components/header';
import { Footer } from '@/components/footer';
import { CartDrawer } from '@/components/cart-drawer';
import { BannerStrip } from '@/components/banner-strip';
import { getSettings } from '@/lib/repo';
import { bridgeStatusCloud } from '@/lib/delivery';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { currentUser } from '@/lib/auth';

export const dynamic = 'force-dynamic';

export default async function SiteLayout({ children }: { children: React.ReactNode }) {
  await cloudSyncLocalDb();
  const settings = getSettings();
  const user = await currentUser();
  const bridge = await bridgeStatusCloud();
  const online = bridge.connected ? bridge.online : Number(settings.online_base || 0);
  const max = bridge.connected && bridge.max > 0 ? bridge.max : Number(settings.bridge_max || settings.online_peak || 0);

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
        ip={settings.server_ip || 'kinggrief.pickme.cloud'}
        version={bridge.mcVersion || settings.server_version || '1.16.5 — 1.21.11'}
        discord={settings.discord_url || 'https://discord.gg/7Cugj4X3eU'}
        telegram={settings.telegram_url || 'https://t.me/kinggrief'}
        supportEmail={settings.support_email || 'support@kinggrief.net'}
      />
      <CartDrawer />
    </div>
  );
}
