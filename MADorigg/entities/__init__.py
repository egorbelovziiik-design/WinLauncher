"""Game actors: bosses, soldiers, projectiles and army bookkeeping."""

from __future__ import annotations

from .army import Army
from .boss import Boss
from .projectile import MegaAttack, Projectile
from .soldier import Soldier

__all__ = ["Army", "Boss", "MegaAttack", "Projectile", "Soldier"]
