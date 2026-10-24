import { NextResponse } from 'next/server';
import { endUserSession } from '@/lib/auth';

export async function POST() {
  await endUserSession();
  return NextResponse.json({ ok: true });
}
