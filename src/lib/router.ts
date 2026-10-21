import { useEffect, useState } from 'react';

export type Route =
  | 'home'
  | 'versions'
  | 'mods'
  | 'modpacks'
  | 'downloads'
  | 'accounts'
  | 'settings';

export const ROUTES: Route[] = ['home', 'versions', 'mods', 'modpacks', 'downloads', 'accounts', 'settings'];

function read(): Route {
  const raw = window.location.hash.replace(/^#\/?/, '').split('?')[0];
  return (ROUTES as string[]).includes(raw) ? (raw as Route) : 'home';
}

export function navigate(route: Route) {
  if (window.location.hash !== `#/${route}`) {
    window.location.hash = `#/${route}`;
  }
}

export function useRoute(): [Route, (r: Route) => void] {
  const [route, setRoute] = useState<Route>(read);
  useEffect(() => {
    const onChange = () => setRoute(read());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return [route, (r) => navigate(r)];
}
