import { NextResponse } from 'next/server';
import { currentUser } from '@/lib/auth';
import { cloudSyncLocalDb } from '@/lib/cloud-store';

export const dynamic = 'force-dynamic';

export async function GET() {
  await cloudSyncLocalDb();
  const user = await currentUser();
  return NextResponse.json({ user }, { headers: { 'Cache-Control': 'no-store' } });
}
