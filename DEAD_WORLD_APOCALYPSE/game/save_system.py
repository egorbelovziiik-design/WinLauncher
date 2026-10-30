"""Save system: JSON slots with checksum integrity, autosave slot.
Game state dict is assembled by the engine; (de)serialization of sub-objects
lives with their classes."""
import hashlib, json, os, time
from game.config import user_data_dir

SAVE_VERSION = 1
SLOTS = ("autosave", "slot1", "slot2", "slot3")


def save_path(slot):
    if slot not in SLOTS:
        raise ValueError(f"unknown slot {slot}")
    return os.path.join(user_data_dir(), f"save_{slot}.json")


def _checksum(payload_bytes):
    return hashlib.sha256(payload_bytes).hexdigest()[:16]


def save_game(state, slot="slot1"):
    envelope = {
        "version": SAVE_VERSION,
        "saved_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "slot": slot,
        "state": state,
    }
    raw = json.dumps(envelope, ensure_ascii=False, sort_keys=True).encode("utf-8")
    envelope["checksum"] = _checksum(raw)
    path = save_path(slot)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(envelope, f, ensure_ascii=False, indent=1)
    os.replace(tmp, path)
    return path


def load_game(slot="slot1"):
    """Returns state dict or None. Raises ValueError on corrupted checksum."""
    path = save_path(slot)
    if not os.path.exists(path):
        return None
    envelope = json.load(open(path, encoding="utf-8"))
    checksum = envelope.pop("checksum", None)
    raw = json.dumps(envelope, ensure_ascii=False, sort_keys=True).encode("utf-8")
    # note: checksum was computed over the envelope *before* adding the field,
    # with identical key order (sort_keys) -> recompute over same content:
    if checksum is not None and _checksum(raw) != checksum:
        raise ValueError("Сохранение повреждено (не совпала контрольная сумма)")
    return envelope.get("state")


def slot_info(slot):
    path = save_path(slot)
    if not os.path.exists(path):
        return None
    try:
        env = json.load(open(path, encoding="utf-8"))
        st = env.get("state", {})
        return {"slot": slot, "saved_at": env.get("saved_at", "?"),
                "day": st.get("day", 1), "kills": st.get("kills", 0),
                "hp": round(st.get("stats", {}).get("health", 0))}
    except Exception:
        return {"slot": slot, "saved_at": "повреждено", "day": "?", "kills": "?", "hp": "?"}


def list_saves():
    return [info for info in (slot_info(s) for s in SLOTS) if info]


def has_continue():
    return any(os.path.exists(save_path(s)) for s in SLOTS)


def newest_slot():
    best, best_t = None, -1
    for s in SLOTS:
        p = save_path(s)
        if os.path.exists(p) and os.path.getmtime(p) > best_t:
            best, best_t = s, os.path.getmtime(p)
    return best
