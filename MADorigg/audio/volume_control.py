"""System master-volume control for Windows (pycaw / Core Audio API).

The prank must *always* leave the machine the way it found it, therefore every
mutation goes through :class:`VolumeControl`, which keeps a snapshot of the
original volume and mute state and can restore it even from a crash handler.

On non-Windows machines (or when ``pycaw`` is missing) the class degrades to a
silent no-op: the show still runs, it simply does not touch the mixer.
"""

from __future__ import annotations

import logging
import sys
import threading
import time
from dataclasses import dataclass
from typing import Optional

LOG = logging.getLogger(__name__)

try:  # pragma: no cover - platform dependent
    from comtypes import CLSCTX_ALL  # type: ignore
    from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume  # type: ignore

    _PYCAW_IMPORT_ERROR: Optional[BaseException] = None
except BaseException as _exc:  # pragma: no cover - platform dependent
    AudioUtilities = None  # type: ignore[assignment]
    IAudioEndpointVolume = None  # type: ignore[assignment]
    CLSCTX_ALL = 0  # type: ignore[assignment]
    _PYCAW_IMPORT_ERROR = _exc


@dataclass(frozen=True)
class AudioState:
    """Snapshot of the system mixer taken before the show starts."""

    volume: Optional[float]
    muted: Optional[bool]

    @property
    def usable(self) -> bool:
        """True when the snapshot actually contains a volume level."""
        return self.volume is not None


@dataclass(frozen=True)
class EqualizerState:
    """Snapshot of the equaliser settings.

    The Windows Core Audio API does not expose the per-device equaliser to
    third-party processes, so MADorigg never *changes* the system EQ.  The
    loudness curve (bass +12 dB, treble +6 dB) is applied inside our own DSP bus
    and this snapshot records the neutral baseline we always restore to.
    """

    bass_db: float = 0.0
    treble_db: float = 0.0
    source: str = "dsp"

    def as_dict(self) -> dict:
        """Return a JSON-friendly representation (handy for logging)."""
        return {"bass_db": self.bass_db, "treble_db": self.treble_db, "source": self.source}


def capture_equalizer() -> EqualizerState:
    """Read the current equaliser settings (best effort, never raises)."""
    return EqualizerState()


def restore_equalizer(state: EqualizerState | None) -> None:
    """Restore an equaliser snapshot.

    MADorigg only ever applies its EQ inside its own DSP chain, so restoring is
    a no-op by design - the OS equaliser was never modified.
    """
    if state is None:
        return
    LOG.debug("Equalizer already neutral (source=%s)", state.source)


class VolumeControl:
    """Save / ramp / restore the Windows master volume.

    Parameters
    ----------
    enabled:
        When ``False`` the object never touches the OS mixer.  Useful for
        ``--no-volume`` runs and for automated tests.
    """

    def __init__(self, enabled: bool = True) -> None:
        self.enabled = bool(enabled)
        self._endpoint = None
        self._saved: Optional[AudioState] = None
        self._equalizer: Optional[EqualizerState] = None
        self._lock = threading.RLock()
        self._stop = threading.Event()
        self._fade_thread: Optional[threading.Thread] = None

        if not self.enabled:
            LOG.info("Volume control disabled by configuration")
            return
        if sys.platform != "win32":
            LOG.info("Volume control unavailable: not running on Windows")
            return
        if AudioUtilities is None:
            LOG.info("Volume control unavailable: pycaw/comtypes not importable (%s)",
                     _PYCAW_IMPORT_ERROR)
            return
        self._open_endpoint()

    # ------------------------------------------------------------------ #
    # Low level plumbing
    # ------------------------------------------------------------------ #
    def _open_endpoint(self) -> None:
        """Activate the default render endpoint (never raises)."""
        try:  # pycaw >= 20230407 style first
            import comtypes  # type: ignore

            speakers = AudioUtilities.GetSpeakers()  # type: ignore[union-attr]
            interface = speakers.Activate(  # type: ignore[union-attr]
                IAudioEndpointVolume._iid_, CLSCTX_ALL, None
            )
            try:
                self._endpoint = comtypes.cast(
                    interface, comtypes.POINTER(IAudioEndpointVolume)
                )
            except BaseException:
                self._endpoint = interface.QueryInterface(IAudioEndpointVolume)
        except BaseException:
            try:  # legacy pycaw style
                from pycaw.pycaw import AudioUtilities as _AU  # type: ignore

                device = self._legacy_device(_AU)
                self._endpoint = device.EndpointVolume  # type: ignore[union-attr]
            except BaseException as exc:  # pragma: no cover - platform dependent
                LOG.warning("Could not open audio endpoint: %s", exc)
                self._endpoint = None
        if self._endpoint is not None:
            LOG.info("Windows audio endpoint acquired")

    @staticmethod
    def _legacy_device(module) -> object:
        """Return the default device object for old pycaw releases."""
        device = module.GetSpeakers()
        if hasattr(device, "EndpointVolume"):
            return device
        enumerator = module.GetDeviceEnumerator()  # type: ignore[attr-defined]
        return enumerator.GetDefaultAudioEndpoint(0, 1)

    @property
    def available(self) -> bool:
        """True when the OS mixer is under our control."""
        return self._endpoint is not None

    @property
    def saved_volume(self) -> Optional[float]:
        """The volume captured by :meth:`save_volume` (``None`` if unknown)."""
        if self._saved is None:
            return None
        return self._saved.volume

    # ------------------------------------------------------------------ #
    # Volume primitives
    # ------------------------------------------------------------------ #
    def get_volume(self) -> Optional[float]:
        """Current master volume in ``0.0 .. 1.0`` (``None`` if unavailable)."""
        if not self.available:
            return None
        try:
            with self._lock:
                return float(self._endpoint.GetMasterVolumeLevelScalar())
        except BaseException as exc:  # pragma: no cover - platform dependent
            LOG.debug("get_volume failed: %s", exc)
            return None

    def get_mute(self) -> Optional[bool]:
        """Current mute flag (``None`` if unavailable)."""
        if not self.available:
            return None
        try:
            with self._lock:
                return bool(self._endpoint.GetMute())
        except BaseException as exc:  # pragma: no cover - platform dependent
            LOG.debug("get_mute failed: %s", exc)
            return None

    def set_volume(self, level: float) -> None:
        """Set the master volume, clamped to ``0.0 .. 1.0``."""
        if not self.available:
            return
        level = max(0.0, min(1.0, float(level)))
        try:
            with self._lock:
                self._endpoint.SetMasterVolumeLevelScalar(level, None)
        except BaseException as exc:  # pragma: no cover - platform dependent
            LOG.debug("set_volume(%.3f) failed: %s", level, exc)

    def set_mute(self, muted: bool) -> None:
        """Mute / unmute the default render device."""
        if not self.available:
            return
        try:
            with self._lock:
                self._endpoint.SetMute(1 if muted else 0, None)
        except BaseException as exc:  # pragma: no cover - platform dependent
            LOG.debug("set_mute(%s) failed: %s", muted, exc)

    def max_volume(self) -> None:
        """Convenience helper: jump straight to 100 %."""
        self.set_volume(1.0)

    # ------------------------------------------------------------------ #
    # Snapshot / restore
    # ------------------------------------------------------------------ #
    def save_volume(self) -> Optional[float]:
        """Remember the current volume (and mute state) and return the level."""
        self._saved = AudioState(volume=self.get_volume(), muted=self.get_mute())
        self._equalizer = capture_equalizer()
        LOG.info("Saved system audio state: %s", self._saved)
        return self._saved.volume

    def restore_volume(self) -> None:
        """Put the system back exactly where we found it (idempotent)."""
        self.cancel_fade()
        if self._saved is None:
            return
        if not self.available:
            return
        target = self._saved.volume
        if target is not None:
            current = self.get_volume()
            if current is not None and abs(current - target) > 0.01:
                self._ramp(target, min(1.0, abs(current - target)))
            else:
                self.set_volume(target)
        if self._saved.muted is not None:
            self.set_mute(self._saved.muted)
        restore_equalizer(self._equalizer)

    # ------------------------------------------------------------------ #
    # Fades
    # ------------------------------------------------------------------ #
    def fade_to(self, target: float, duration: float) -> None:
        """Smoothly move the volume to ``target`` over ``duration`` seconds.

        Runs in a daemon thread so the render loop never blocks.
        """
        if not self.available:
            return
        start = self.get_volume()
        if start is None:
            self.set_volume(target)
            return
        target = max(0.0, min(1.0, float(target)))
        duration = max(0.0, float(duration))
        if duration <= 0.0 or abs(start - target) < 0.001:
            self.set_volume(target)
            return

        self.cancel_fade()
        stop_event = self._stop = threading.Event()

        def _worker() -> None:
            # COM must be initialised on every thread that talks to pycaw.
            comtypes_module = None
            try:
                import comtypes as comtypes_module  # type: ignore

                comtypes_module.CoInitialize()
            except BaseException:  # pragma: no cover - platform dependent
                comtypes_module = None
            try:
                steps = max(1, int(duration * 100.0))
                delay = duration / steps
                for step in range(1, steps + 1):
                    if stop_event.is_set():
                        return
                    level = start + (target - start) * (step / steps)
                    self.set_volume(level)
                    if stop_event.wait(delay):
                        return
                self.set_volume(target)
            finally:
                if comtypes_module is not None:  # pragma: no cover - Windows only
                    try:
                        comtypes_module.CoUninitialize()
                    except BaseException:
                        pass

        self._fade_thread = threading.Thread(
            target=_worker, name="madorigg-volume-fade", daemon=True
        )
        self._fade_thread.start()

    def _ramp(self, target: float, duration: float) -> None:
        """Blocking ramp used by :meth:`restore_volume` (short and safe)."""
        start = self.get_volume()
        if start is None:
            self.set_volume(target)
            return
        steps = max(1, int(duration * 50.0))
        for step in range(1, steps + 1):
            self.set_volume(start + (target - start) * step / steps)
            time.sleep(duration / steps)
        self.set_volume(target)

    def cancel_fade(self) -> None:
        """Stop a running fade thread (safe to call at any time)."""
        thread = self._fade_thread
        if thread is None:
            return
        self._stop.set()
        if thread.is_alive():
            thread.join(timeout=0.25)
        self._fade_thread = None

    def close(self) -> None:
        """Cancel fades and release the endpoint reference."""
        self.cancel_fade()
        self._endpoint = None
