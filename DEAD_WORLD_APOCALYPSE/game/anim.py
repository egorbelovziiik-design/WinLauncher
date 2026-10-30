"""Keyframe animation sampling + two-layer blending (base locomotion + one-shot
overlay). Pure math over the semantic joint tables exported from Blender
(assets/animations/zombie_animations.json).

Joints use (pitch, yaw, roll) degrees: pitch>0 = swing/lean forward.
The engine layer maps these onto Ursina part rotations."""
import json, math, os

JOINTS = ["hips", "spine", "head",
          "upper_arm.L", "forearm.L", "hand.L",
          "upper_arm.R", "forearm.R", "hand.R",
          "thigh.L", "shin.L", "foot.L",
          "thigh.R", "shin.R", "foot.R"]
ZERO = (0.0, 0.0, 0.0)


def _lerp(a, b, t):
    return a + (b - a) * t


def _lerp3(a, b, t):
    return (_lerp(a[0], b[0], t), _lerp(a[1], b[1], t), _lerp(a[2], b[2], t))


def _smooth(t):
    return 0.5 - 0.5 * math.cos(math.pi * t)  # cosine ease in/out


class AnimClip:
    def __init__(self, name, data):
        self.name = name
        self.fps = data.get("fps", 24)
        self.loop = data.get("loop", True)
        self.keys = [(int(f), {j: tuple(float(x) for x in v) for j, v in jd.items()})
                     for f, jd in data["keys"]]
        self.keys.sort(key=lambda k: k[0])
        # densify: every key carries full joint state (carry-forward)
        state = {j: ZERO for j in JOINTS}
        dense = []
        for f, jd in self.keys:
            state = dict(state)
            state.update(jd)
            dense.append((f, dict(state)))
        self.keys = dense
        self.end_frame = self.keys[-1][0] if self.keys else 0
        self.duration = self.end_frame / self.fps if self.fps else 0.0

    def sample(self, time_sec):
        if not self.keys or self.duration <= 0:
            return {j: ZERO for j in JOINTS}
        if self.loop:
            t = time_sec % self.duration
        else:
            t = min(time_sec, self.duration)
        frame = t * self.fps
        keys = self.keys
        if frame <= keys[0][0]:
            return dict(keys[0][1])
        for i in range(len(keys) - 1):
            f0, s0 = keys[i]
            f1, s1 = keys[i + 1]
            if f0 <= frame <= f1:
                u = 0.0 if f1 == f0 else (frame - f0) / (f1 - f0)
                u = _smooth(u)
                return {j: _lerp3(s0[j], s1[j], u) for j in JOINTS}
        return dict(keys[-1][1])

    def finished(self, time_sec):
        return (not self.loop) and time_sec >= self.duration


class AnimationLibrary:
    def __init__(self, path=None):
        self.clips = {}
        if path and os.path.exists(path):
            data = json.load(open(path, encoding="utf-8"))
            for name, d in data.items():
                self.clips[name] = AnimClip(name, d)

    def get(self, name):
        return self.clips.get(name)

    def __contains__(self, name):
        return name in self.clips

    def names(self):
        return sorted(self.clips.keys())


def blend(pose_a, pose_b, w):
    """Blend pose dicts; w = weight of pose_b."""
    if w <= 0:
        return pose_a
    if w >= 1:
        return pose_b
    return {j: _lerp3(pose_a[j], pose_b[j], w) for j in JOINTS}


class Animator:
    """State-machine animator: looping base clip with crossfade, plus one-shot
    overlays (attack/hit/death) that temporarily override the base."""

    def __init__(self, library, base="idle"):
        self.lib = library
        self.base_name = base
        self.prev_base_name = base
        self.base_time = 0.0
        self.prev_base_time = 0.0
        self.crossfade = 1.0          # 1 = fully on current base
        self.crossfade_speed = 5.0
        self.overlay_name = None
        self.overlay_time = 0.0
        self.overlay_weight = 0.0
        self.speed = 1.0

    def set_base(self, name, restart=False):
        if name == self.base_name and not restart:
            return
        if name not in self.lib.clips:
            return
        self.prev_base_name = self.base_name
        self.prev_base_time = self.base_time
        self.base_name = name
        if restart:
            self.base_time = 0.0
        self.crossfade = 0.0

    def play_overlay(self, name):
        if name in self.lib.clips:
            self.overlay_name = name
            self.overlay_time = 0.0
            self.overlay_weight = 0.0

    def update(self, dt):
        self.base_time += dt * self.speed
        self.prev_base_time += dt * self.speed
        if self.crossfade < 1.0:
            self.crossfade = min(1.0, self.crossfade + dt * self.crossfade_speed)
        if self.overlay_name:
            clip = self.lib.clips[self.overlay_name]
            self.overlay_time += dt * self.speed
            if clip.finished(self.overlay_time):
                # release at end (do NOT ramp back up)
                self.overlay_weight -= dt * 8.0
                if self.overlay_weight <= 0:
                    self.overlay_name = None
                    self.overlay_weight = 0.0
            else:
                # fast attack-in, then hold
                self.overlay_weight = min(1.0, self.overlay_weight + dt * 10.0)
        return self.pose()

    def pose(self):
        cur = self.lib.clips.get(self.base_name)
        if cur is None:
            return {j: ZERO for j in JOINTS}
        pose = cur.sample(self.base_time)
        if self.crossfade < 1.0:
            prev = self.lib.clips.get(self.prev_base_name)
            if prev is not None:
                pose = blend(prev.sample(self.prev_base_time), pose, self.crossfade)
        if self.overlay_name and self.overlay_weight > 0:
            over = self.lib.clips[self.overlay_name].sample(self.overlay_time)
            pose = blend(pose, over, self.overlay_weight)
        return pose

    @property
    def overlay_active(self):
        return self.overlay_name is not None


# Semantic (pitch,yaw,roll) -> engine part rotation (Ursina euler degrees).
# Engine space: Y up, character faces +Z, limbs hang along -Y.
#  rotation.x positive pitches the +Y axis toward +Z (forward lean for torso),
#  but rotates a downward limb backward; hence the sign split below.
_UP_JOINTS = {"hips", "spine", "head"}      # geometry extends +Y from pivot
JOINT_TO_PART = {
    "hips": "pelvis", "spine": "torso", "head": "head",
    "upper_arm.L": "upper_arm.L", "forearm.L": "forearm.L", "hand.L": "hand.L",
    "upper_arm.R": "upper_arm.R", "forearm.R": "forearm.R", "hand.R": "hand.R",
    "thigh.L": "thigh.L", "shin.L": "shin.L", "foot.L": "foot.L",
    "thigh.R": "thigh.R", "shin.R": "shin.R", "foot.R": "foot.R",
}


def joint_to_engine_euler(joint, pyr):
    pitch, yaw, roll = pyr
    if joint in _UP_JOINTS:
        return (pitch, yaw, -roll)
    # downward joints: forward swing needs negative X; knees/elbows bend backward
    # relative to the limb direction which is also captured by the same rule
    # because their 'pitch' in the tables means flexion (heel/hand toward back... )
    # - thigh/upper_arm pitch: swing forward     -> rotation.x = -pitch... wait:
    # R_x(+t) maps -Y -> -Z is wrong side; limb tip (0,-1,0):
    #   y'=-cos t, z'=-sin t  => +t moves tip to -Z (backward). So forward = -pitch.
    # - shin/forearm pitch (flexion): tip moves backward => rotation.x = +pitch.
    if joint.startswith(("shin", "forearm", "foot", "hand")):
        return (pitch, yaw, roll)
    return (-pitch, yaw, roll)
