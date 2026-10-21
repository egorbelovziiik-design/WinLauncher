import { useState } from 'react';
import { ArrowRight, Check, Sparkles, Trees } from 'lucide-react';
import { useLauncher, perform } from '../../hooks/useLauncher';
import { themes, getTheme, worldImage } from '../../themes';
import { useT } from '../../locales';
import { Button, PageHeading, Toggle } from '../../components/ui';

export default function Themes() {
  const { snapshot, updateSettings } = useLauncher(); const [preview, setPreview] = useState(snapshot.settings.theme); const theme = getTheme(preview); const t = useT();
  return <div><PageHeading eyebrow={t('ЛИЧНОЕ ПРОСТРАНСТВО')} title={t('Найди свой биом')} description={t('Десять миров. Одно место, куда хочется возвращаться.')} action={<span className="theme-count">10 <span>DYNAMIC WORLDS</span></span>} />
    <div className="theme-preview" style={{ '--preview-accent': theme.accent } as React.CSSProperties}><img src={worldImage(theme.id)} alt={`Предпросмотр темы ${theme.name}`} style={{ filter: theme.hue }} /><div className="theme-preview-shade" /><div><span className="eyebrow"><Trees size={13} /> {theme.tag}</span><h2>{theme.name}</h2><p>{theme.subtitle}</p><Button variant="primary" onClick={() => void perform(() => updateSettings({ theme: theme.id }))}>{snapshot.settings.theme === theme.id ? <><Check size={17} />{t('Активная тема')}</> : <>{t('Применить тему')}<ArrowRight size={17} /></>}</Button></div><span className="preview-label">LIVE PREVIEW <span /></span></div>
    <div className="theme-effects"><div><Sparkles size={20} /><span>Почувствуй атмосферу своего мира<small>Тема меняет всю палитру, свечение, баннеры и частицы. Без перезапуска.</small></span></div><Toggle label={t('Эффекты окружения')} description={t('Частицы и плавные переходы')} checked={snapshot.settings.effects} onChange={effects => void perform(() => updateSettings({ effects }))} /></div>
    <div className="themes-grid">{themes.map((item, i) => <button key={item.id} className={`theme-tile ${preview === item.id ? 'theme-tile-preview' : ''}`} onClick={() => setPreview(item.id)} style={{ '--tile-accent': item.accent, '--tile-background': item.background } as React.CSSProperties} aria-pressed={preview === item.id}><div className="theme-tile-image"><img src={worldImage(item.id)} alt="" style={{ filter: item.hue, objectPosition: `${15 + i * 8}% 50%` }} /><span className="theme-tile-index">{String(i + 1).padStart(2, '0')}</span>{snapshot.settings.theme === item.id && <span className="theme-check"><Check size={15} /></span>}<div className="theme-palette"><i style={{ background: item.background }} /><i style={{ background: item.card }} /><i style={{ background: item.accent }} /></div></div><div className="theme-tile-info"><strong>{item.name}</strong><small>{item.subtitle}</small></div></button>)}</div>
  </div>;
}
