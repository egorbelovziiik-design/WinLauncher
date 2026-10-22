// Public release assets checked against GitHub on 2026-10-22.
// This is a deliberate native-channel pin, not /releases/latest (another
// application channel also exists in this repository).
export const release = {
  version: '1.0.1',
  status: 'Preview',
  tag: 'winlauncher-native-v1.0.1-preview.1',
  page: 'https://github.com/egorbelovziiik-design/WinLauncher/releases/tag/winlauncher-native-v1.0.1-preview.1',
  source: 'https://github.com/egorbelovziiik-design/WinLauncher/tree/arena/4f921e32-winlauncher',
  guide: 'https://github.com/egorbelovziiik-design/WinLauncher/blob/arena/4f921e32-winlauncher/docs/QUICKSTART-1.0.1.ru.txt',
  testing: 'https://github.com/egorbelovziiik-design/WinLauncher/blob/arena/4f921e32-winlauncher/docs/verification/fabric-1.0.1/TESTING.md',
  issues: 'https://github.com/egorbelovziiik-design/WinLauncher/issues/new?title=WinLauncher%201.0.1%3A%20&body=%D0%92%D0%B5%D1%80%D1%81%D0%B8%D1%8F%20Windows%3A%0A%D0%92%D0%B5%D1%80%D1%81%D0%B8%D1%8F%20Minecraft%20%D0%B8%20%D0%B7%D0%B0%D0%B3%D1%80%D1%83%D0%B7%D1%87%D0%B8%D0%BA%3A%0A%D0%A8%D0%B0%D0%B3%D0%B8%20%D0%B4%D0%BB%D1%8F%20%D0%BF%D0%BE%D0%B2%D1%82%D0%BE%D1%80%D0%B0%3A%0A%0A%D0%9D%D0%B5%20%D0%BF%D1%83%D0%B1%D0%BB%D0%B8%D0%BA%D1%83%D0%B9%D1%82%D0%B5%20%D0%BF%D0%B0%D1%80%D0%BE%D0%BB%D0%B8%2C%20API-%D0%BA%D0%BB%D1%8E%D1%87%D0%B8%20%D0%B8%20accessToken.',
  files: {
    setup: {
      name: 'WinLauncher-Setup.exe',
      label: 'Установщик',
      description: 'Установит приложение, создаст ярлыки и предложит WebView2, если его нет.',
      bytes: 6034114,
      size: '6,03 МБ',
      url: 'https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.1-preview.1/WinLauncher-Setup.exe',
      sha256: '1f5234717f057e6b7d688edb4de1d553f6a73d372db2ea6d21f976c093a38120',
    },
    standalone: {
      name: 'WinLauncher.exe',
      label: 'Отдельный EXE',
      description: 'Запуск без установщика. Требуется уже установленный Microsoft Edge WebView2 Runtime.',
      bytes: 19333120,
      size: '19,33 МБ',
      url: 'https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.1-preview.1/WinLauncher.exe',
      sha256: 'ce984a5f1c16ccb45e84a0de357bab0fb0aac6e2e9288bf0a02e0daf844be635',
    },
  },
} as const;
