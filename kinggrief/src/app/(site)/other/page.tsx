import type { Metadata } from 'next';
import Link from 'next/link';
import { AlertTriangle, Info, MessageSquareOff, ShieldAlert, Unlock } from 'lucide-react';
import { listProducts } from '@/lib/repo';
import { ServiceCard } from '@/components/product-card';
import { Accordion, Reveal, SectionHeading } from '@/components/ui';
import { Particles } from '@/components/voxel';

export const metadata: Metadata = {
  title: 'Разбан и размут — снятие наказаний',
  description:
    'Разбан 99 ₽, размут 49 ₽, разбан по IP 199 ₽, размут по IP 99 ₽. Снятие блокировок по правилам сервера KingGrief.',
  alternates: { canonical: '/other' },
};

const FAQ = [
  {
    q: 'Какие наказания можно снять?',
    a: 'Автоматические и стандартные блокировки: бан за гриферство, мут за флуд или оскорбления без угроз. Не снимаются: читы, дюп, обход блокировки, реклама других проектов, угрозы и доксинг.',
  },
  {
    q: 'Как быстро снимут наказание?',
    a: 'Обычно в течение 5 минут после подтверждения оплаты, если сервер онлайн. Для IP-наказаний — до 30 минут: администратор проверяет адрес вручную.',
  },
  {
    q: 'Что если наказание не подлежит снятию?',
    a: 'Мы проверим заказ и вернём деньги на тот же способ оплаты в течение 1–3 рабочих дней. Это единственная ситуация, когда возможен возврат за услугу.',
  },
  {
    q: 'Нужно ли быть онлайн?',
    a: 'Нет. Достаточно указать точный никнейм при оформлении заказа. Для IP-услуг желательно зайти на сервер с того же адреса после выдачи.',
  },
];

export default function OtherPage() {
  const products = listProducts({ kind: 'other', activeOnly: true });

  return (
    <>
      <section className="relative overflow-hidden border-b border-neon/10">
        <div className="pointer-events-none absolute inset-0 grid-bg opacity-50" />
        <Particles density={20} className="absolute inset-0 h-full w-full opacity-60" />
        <div className="container-x relative py-10 md:py-14">
          <nav aria-label="Хлебные крошки" className="mb-6 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
            <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-neon">Другое</span>
          </nav>
          <div className="max-w-2xl">
            <span className="chip mb-4"><Unlock size={12} /> снятие наказаний</span>
            <h1 className="display text-[2.6rem] leading-[0.9] text-white sm:text-5xl lg:text-6xl">
              Разбан и <span className="text-neon">размут</span>
            </h1>
            <p className="mt-4 text-[0.9rem] leading-relaxed text-white/45">
              Четыре услуги для быстрого возвращения в игру. Мы выдаём только те снятия, которые разрешены правилами —
              без исключений для читов и обхода блокировок.
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              <span className="chip !border-emerald/25 !bg-emerald/8 !text-emerald"><Unlock size={11} /> разбан</span>
              <span className="chip !border-blue/25 !bg-blue/8 !text-blue"><MessageSquareOff size={11} /> размут</span>
              <span className="chip !border-neon/25 !bg-neon/8 !text-white/70"><ShieldAlert size={11} /> по IP</span>
            </div>
          </div>
        </div>
      </section>

      <section className="container-x py-10 md:py-14">
        <SectionHeading eyebrow="услуги" title="Выбери нужное" description="Никнейм указывается при оформлении. Проверьте его внимательно — после оплаты изменить получателя нельзя." />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {products.map((product, i) => (
            <Reveal key={product.id} delay={i * 0.05} className="h-full">
              <ServiceCard product={product} />
            </Reveal>
          ))}
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-2">
          <div className="flex items-start gap-3 rounded-xl border border-amber/25 bg-amber/8 p-4">
            <AlertTriangle size={17} className="mt-0.5 shrink-0 text-amber" />
            <div>
              <p className="text-[0.8rem] font-bold text-white">Что не снимается</p>
              <p className="mt-1 text-[0.72rem] leading-relaxed text-white/45">
                Читы, дюпы, обход бана, реклама сторонних проектов, угрозы, доксинг и мошенничество. В таких случаях
                средства возвращаются на исходный способ оплаты.
              </p>
            </div>
          </div>
          <div className="flex items-start gap-3 rounded-xl border border-neon/20 bg-neon/[0.05] p-4">
            <Info size={17} className="mt-0.5 shrink-0 text-neon" />
            <div>
              <p className="text-[0.8rem] font-bold text-white">IP-услуги</p>
              <p className="mt-1 text-[0.72rem] leading-relaxed text-white/45">
                Снимают блокировку с адреса, с которого играл указанный никнейм. Если блокировка выдана за бот-атаку,
                сначала обратитесь в поддержку.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="border-y border-white/6 bg-abyss/40 py-12">
        <div className="container-x grid gap-8 lg:grid-cols-[0.8fr_1.2fr]">
          <SectionHeading eyebrow="faq" title="Вопросы по снятию" />
          <Accordion items={FAQ} />
        </div>
      </section>

      <section className="container-x py-12">
        <div className="rounded-2xl border border-white/8 bg-panel/40 p-5 text-center">
          <p className="text-[0.8rem] text-white/45">
            Уверены, что наказание выдано ошибочно? Апелляция рассматривается бесплатно — напишите в поддержку до покупки.
          </p>
          <Link href="/support" className="btn btn-ghost btn-sm mt-3">Перейти в поддержку</Link>
        </div>
      </section>
    </>
  );
}
