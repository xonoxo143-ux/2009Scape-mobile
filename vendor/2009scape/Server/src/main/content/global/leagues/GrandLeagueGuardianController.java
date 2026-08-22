package content.global.leagues;

import content.global.leagues.core.LeagueGuardianCombat;
import content.global.leagues.core.LeagueGuardianModifiers;
import core.game.interaction.MovementPulse;
import core.game.node.entity.Entity;
import core.game.node.entity.combat.CombatStyle;
import core.game.node.entity.combat.DeathTask;
import core.game.node.entity.combat.ImpactHandler.HitsplatType;
import core.game.node.entity.combat.equipment.WeaponInterface;
import core.game.node.entity.impl.Projectile;
import core.game.node.entity.impl.PulseType;
import core.game.node.entity.npc.NPC;
import core.game.node.entity.player.Player;
import core.game.system.task.Pulse;
import core.game.world.GameWorld;
import core.game.world.map.Location;
import core.game.world.map.RegionManager;
import core.game.world.map.path.Pathfinder;
import core.game.world.map.zone.ZoneRestriction;
import core.game.world.update.flag.context.Animation;
import core.tools.RandomFunction;

/** Owns the single, data-driven Guardian companion attached to a League player. */
public final class GrandLeagueGuardianController {

    private static final String ATTRIBUTE = "grand-league-guardian";
    private static final String PENDING_ATTRIBUTE = "grand-league-guardian-pending";

    private GrandLeagueGuardianController() {
    }

    public static void sync(Player player) {
        LeagueGuardianModifiers modifiers = GrandLeagueManager.guardianModifiers(player);
        GrandLeagueGuardian guardian = player.getAttribute(ATTRIBUTE);
        if (!modifiers.getEnabled()) {
            clear(player);
            return;
        }
        if (!player.getAttribute("logged-in-fully", false)) {
            if (!player.getAttribute(PENDING_ATTRIBUTE, false)) {
                player.setAttribute(PENDING_ATTRIBUTE, true);
                GameWorld.getPulser().submit(new Pulse(1, player) {
                    @Override
                    public boolean pulse() {
                        player.removeAttribute(PENDING_ATTRIBUTE);
                        if (player.isActive()) {
                            sync(player);
                        }
                        return true;
                    }
                });
            }
            return;
        }
        if (guardian != null && guardian.isActive()) {
            guardian.updateModifiers(modifiers);
            return;
        }
        guardian = new GrandLeagueGuardian(player, modifiers);
        player.setAttribute(ATTRIBUTE, guardian);
        guardian.spawnBesideOwner();
    }

    public static void clear(Player player) {
        player.removeAttribute(PENDING_ATTRIBUTE);
        GrandLeagueGuardian guardian = player.getAttribute(ATTRIBUTE);
        if (guardian != null) {
            guardian.dismiss();
        } else {
            player.removeAttribute(ATTRIBUTE);
        }
    }

    private static final class GrandLeagueGuardian extends NPC {

        private static final int NPC_ID = 7343; // Steel titan: present in the unmodified 2009 cache.
        private static final int ATTACK_ANIMATION = 8183;
        private static final int ATTACK_PROJECTILE = 1445;

        private final Player owner;
        private LeagueGuardianModifiers modifiers;
        private int nextAttackTick;

        private GrandLeagueGuardian(Player owner, LeagueGuardianModifiers modifiers) {
            super(NPC_ID, owner.getLocation());
            this.owner = owner;
            this.modifiers = modifiers;
            setRespawn(false);
            setWalks(false);
        }

        private void spawnBesideOwner() {
            Location spawn = RegionManager.getSpawnLocation(owner, this);
            if (spawn != null) {
                location = spawn;
            }
            init();
            startFollowing();
        }

        private void updateModifiers(LeagueGuardianModifiers modifiers) {
            this.modifiers = modifiers;
        }

        @Override
        public void configure() {
            getProperties().setNPCWalkable(true);
            getProperties().setAttackStyle(new WeaponInterface.AttackStyle(
                WeaponInterface.STYLE_RANGE_ACCURATE,
                WeaponInterface.BONUS_RANGE
            ));
        }

        @Override
        public void handleTickActions() {
            LeagueGuardianModifiers current = GrandLeagueManager.guardianModifiers(owner);
            if (!owner.isActive() || !current.getEnabled()) {
                dismiss();
                return;
            }
            updateModifiers(current);

            boolean restricted = owner.getZoneMonitor().isRestricted(ZoneRestriction.FOLLOWERS)
                && !owner.getLocks().isLocked("enable_summoning");
            setInvisible(restricted);
            if (restricted) {
                return;
            }

            if (getLocation().getZ() != owner.getLocation().getZ()
                || getLocation().getDistance(owner.getLocation()) > 12) {
                rejoinOwner();
            } else if (!getPulseManager().hasPulseRunning()) {
                startFollowing();
            }

            Entity target = owner.getProperties().getCombatPulse().getVictim();
            if (target == null) {
                target = owner.getAttribute("combat-attacker");
            }
            if (!(target instanceof NPC)
                || !target.isActive()
                || target.isInvisible()
                || DeathTask.isDead(target)
                || target.getLocation().getZ() != getLocation().getZ()
                || target.getLocation().getDistance(getLocation()) > 12
                || GameWorld.getTicks() < nextAttackTick) {
                return;
            }

            strike(target);
            nextAttackTick = GameWorld.getTicks() + modifiers.getAttackIntervalTicks();
        }

        private void strike(Entity target) {
            int defenceRoll = weakestDefenceRoll(target);
            boolean accurate = LeagueGuardianCombat.INSTANCE.isAccurate(
                modifiers.getAccuracyRoll(),
                defenceRoll,
                Math.random()
            );
            int damage = accurate
                ? RandomFunction.random(modifiers.getMinimumHit(), modifiers.getMaximumHit() + 1)
                : 0;
            faceTemporary(target, 2);
            animate(Animation.create(ATTACK_ANIMATION));
            Projectile.magic(this, target, ATTACK_PROJECTILE, 60, 36, 41, 46).send();
            target.getImpactHandler().manualHit(owner, damage,
                damage > 0 ? HitsplatType.NORMAL : HitsplatType.MISS, 1);
        }

        private int weakestDefenceRoll(Entity target) {
            int weakest = Integer.MAX_VALUE;
            for (int bonus = WeaponInterface.BONUS_STAB; bonus <= WeaponInterface.BONUS_CRUSH; bonus++) {
                getProperties().setAttackStyle(new WeaponInterface.AttackStyle(
                    WeaponInterface.STYLE_CONTROLLED, bonus));
                weakest = Math.min(weakest,
                    CombatStyle.MELEE.getSwingHandler().calculateDefence(target, this));
            }
            getProperties().setAttackStyle(new WeaponInterface.AttackStyle(
                WeaponInterface.STYLE_RANGE_ACCURATE, WeaponInterface.BONUS_RANGE));
            weakest = Math.min(weakest,
                CombatStyle.RANGE.getSwingHandler().calculateDefence(target, this));
            getProperties().setAttackStyle(new WeaponInterface.AttackStyle(
                WeaponInterface.STYLE_CAST, WeaponInterface.BONUS_MAGIC));
            weakest = Math.min(weakest,
                CombatStyle.MAGIC.getSwingHandler().calculateDefence(target, this));
            return Math.max(0, weakest);
        }

        private void startFollowing() {
            getPulseManager().run(new MovementPulse(this, owner, Pathfinder.DUMB) {
                @Override
                public boolean pulse() {
                    return false;
                }
            }, PulseType.STANDARD);
            face(owner);
        }

        private void rejoinOwner() {
            Location spawn = RegionManager.getSpawnLocation(owner, this);
            teleport(spawn == null ? owner.getLocation() : spawn);
            startFollowing();
        }

        @Override
        public void onRegionInactivity() {
            if (!owner.isActive()) {
                dismiss();
            } else {
                rejoinOwner();
            }
        }

        @Override
        public boolean isAttackable(Entity entity, CombatStyle style, boolean message) {
            return false;
        }

        @Override
        public void startDeath(Entity killer) {
            fullRestore();
        }

        private void dismiss() {
            if (isActive()) {
                clear();
            }
            if (owner.getAttribute(ATTRIBUTE) == this) {
                owner.removeAttribute(ATTRIBUTE);
            }
        }
    }
}
