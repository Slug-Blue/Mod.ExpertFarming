package io.github.slugblue.wildroots.butterfly;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.pollination.BeePollen;
import io.github.slugblue.wildroots.pollination.BloomIndex;
import io.github.slugblue.wildroots.pollination.PollinationService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Daytime pollinator with collision-aware flight and supported, covered night perches. */
public final class ButterflyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(ButterflyEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(ButterflyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(ButterflyEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HANGING = SynchedEntityData.defineId(ButterflyEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FEEDING = SynchedEntityData.defineId(ButterflyEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int RAIN_TOLERANCE = 70;
    public static final int RAIN_WARNING = 10;
    private static final int NIGHT_CYCLE = 600;
    private static final int NIGHT_ACTIVE = 60;
    private final BeePollen pollen = new BeePollen();
    private BlockPos shelter;
    private BlockPos flower;
    private int flowerDeadline;
    private final java.util.LinkedHashMap<Long, Integer> recentFlowers = new java.util.LinkedHashMap<>();
    private int wetTicks;
    private int hoverTicks;
    private Vec3 flightTarget;
    private int targetUntil;
    private Vec3 detour;
    private int detourUntil;
    private int shelterSearchAt;
    private int flowerSearchAt;
    private boolean avoidingRain;

    public ButterflyEntity(EntityType<? extends ButterflyEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        entityData.set(VARIANT, random.nextInt(3));
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 2).add(Attributes.MOVEMENT_SPEED, .18)
                .add(Attributes.FLYING_SPEED, .3).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(false);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(RESTING, false);
        entityData.define(VARIANT, 0);
        entityData.define(FLYING, true);
        entityData.define(HANGING, false);
        entityData.define(FEEDING, false);
    }

    public boolean resting() { return entityData.get(RESTING); }
    public boolean flying() { return entityData.get(FLYING); }
    public boolean feeding() { return entityData.get(FEEDING); }
    public BlockPos flowerTarget() { return flower; }
    public boolean hanging() { return entityData.get(HANGING); }
    public int variant() { return entityData.get(VARIANT); }
    public int wetTicks() { return wetTicks; }
    public BeePollen pollen() { return pollen; }

    /** A cover is any non-raining cell below a roof/canopy. Artificial light is irrelevant. */
    public static boolean covered(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                pos.getX(), pos.getZ()) > pos.getY() && !level.isRainingAt(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    /** Bounded nearest shelter search, only in already loaded chunks. */
    public static BlockPos nearestShelter(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-10, -5, -10), origin.offset(10, 5, 10))) {
            double d = p.distSqr(origin);
            RestSpot spot = d < distance ? restSpot(level, p) : null;
            if (spot != null && visible(level, Vec3.atCenterOf(origin), spot.position().add(0, .17, 0))) {
                best = p.immutable(); distance = d;
            }
        }
        return best;
    }

    private static boolean visible(ServerLevel level, Vec3 from, Vec3 to) {
        BlockPos a = BlockPos.containing(from), b = BlockPos.containing(to);
        // DDA can briefly cross a side chunk between sampled points on a diagonal.
        for (int x = Math.min(a.getX(), b.getX()) >> 4; x <= (Math.max(a.getX(), b.getX()) >> 4); x++)
            for (int z = Math.min(a.getZ(), b.getZ()) >> 4; z <= (Math.max(a.getZ(), b.getZ()) >> 4); z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, null))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** A roof alone is not a perch: there must also be a dry supporting surface. */
    private static Vec3 perch(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) return null;
        var floor = level.getBlockState(pos.below()).getCollisionShape(level, pos.below());
        if (floor.isEmpty() || !level.getFluidState(pos.below()).isEmpty() || !covered(level, pos)) return null;
        return new Vec3(pos.getX() + .5, pos.getY() - 1 + floor.max(net.minecraft.core.Direction.Axis.Y), pos.getZ() + .5);
    }

    private record RestSpot(Vec3 position, boolean hanging) { }

    private static RestSpot restSpot(ServerLevel level, BlockPos pos) {
        Vec3 floor = perch(level, pos);
        if (floor != null) return new RestSpot(floor, false);
        if (!covered(level, pos) || !level.hasChunkAt(pos.above())) return null;
        var ceiling = level.getBlockState(pos.above()).getCollisionShape(level, pos.above());
        if (ceiling.isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return null;
        // Attach the top of the entity's box to the underside; this also supports upper slabs.
        return new RestSpot(new Vec3(pos.getX() + .5,
                pos.getY() + 1 + ceiling.min(net.minecraft.core.Direction.Axis.Y) - .35, pos.getZ() + .5), true);
    }

    /** Weather countdown, not client rain interpolation; deserts/snow never trigger a rain response. */
    public static boolean rainSoon(ServerLevel level, BlockPos pos) {
        if (level.getBiome(pos).value().getPrecipitationAt(pos)
                != net.minecraft.world.level.biome.Biome.Precipitation.RAIN) return false;
        var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
        return data.isRaining() || level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE)
                && data.getClearWeatherTime() == 0 && data.getRainTime() > 0 && data.getRainTime() <= RAIN_WARNING;
    }

    private boolean clear(ServerLevel level, Vec3 at) {
        return level.hasChunkAt(BlockPos.containing(at)) && level.noCollision(this, getBoundingBox().move(at.subtract(position())))
                && level.getFluidState(BlockPos.containing(at)).isEmpty();
    }

    /** Use the same velocity on open terrain and in the air; ground pathfinding cannot stall flight. */
    @Override
    public void travel(Vec3 input) {
        if (!isEffectiveAi()) { super.travel(input); return; }
        move(MoverType.SELF, getDeltaMovement());
        Vec3 velocity = getDeltaMovement().scale(.91);
        if (!isNoGravity()) velocity = velocity.add(0, -.08, 0);
        setDeltaMovement(velocity);
    }

    private void steer(ServerLevel level, Vec3 target, double speed, boolean flight) {
        setNoGravity(flight);
        entityData.set(FLYING, flight);
        Vec3 direction = target.subtract(position());
        if (!flight) direction = new Vec3(direction.x, 0, direction.z);
        if (detour != null && tickCount < detourUntil && flight) direction = detour;
        Vec3 wanted = direction.lengthSqr() < .01 ? Vec3.ZERO : direction.normalize().scale(speed);
        if (flight) {
            Vec3 ahead = wanted.normalize().scale(Math.min(wanted.length() * 4, target.distanceTo(position())));
            if (!clear(level, position().add(ahead)) || horizontalCollision || verticalCollision) {
                // Try several local directions instead of waiting for an impossible path forever.
                Vec3 best = null;
                double score = -Double.MAX_VALUE;
                for (int i = 0; i < 12; i++) {
                    double angle = i * Math.PI / 4;
                    Vec3 candidate = i < 8 ? new Vec3(Math.cos(angle), .45, Math.sin(angle)).normalize()
                            : new Vec3(i == 8 ? 1 : i == 9 ? -1 : 0, i < 10 ? -.6 : i == 10 ? 1 : -1,
                                    i < 10 ? 0 : .25).normalize();
                    if (clear(level, position().add(candidate.scale(.65)))) {
                        double value = candidate.dot(target.subtract(position()).normalize());
                        if (value > score) { best = candidate; score = value; }
                    }
                }
                detour = best;
                detourUntil = tickCount + 12;
                wanted = best == null ? Vec3.ZERO : best.scale(speed);
            }
            Vec3 velocity = getDeltaMovement().scale(.65).add(wanted.scale(.35));
            // A small continuous wingbeat drift; stop bobbing while approaching a perch/flower.
            if (target.distanceToSqr(position()) > 1) velocity = velocity.add(0, Math.sin((tickCount + getId()) * .23) * .004, 0);
            setDeltaMovement(velocity);
        } else setDeltaMovement(wanted.x, Math.min(0, getDeltaMovement().y), wanted.z);
        if (wanted.horizontalDistanceSqr() > .0001) {
            float heading = (float) (Math.atan2(wanted.z, wanted.x) * 180 / Math.PI) - 90;
            setYRot(net.minecraft.util.Mth.rotLerp(.25f, getYRot(), heading));
            setYBodyRot(getYRot()); setYHeadRot(getYRot());
        }
    }

    private void wander(ServerLevel level) {
        if (flightTarget == null || tickCount >= targetUntil || position().distanceToSqr(flightTarget) < .4
                || horizontalCollision || verticalCollision) {
            flightTarget = null;
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = blockPosition().getX() + random.nextInt(11) - 5;
                int z = blockPosition().getZ() + random.nextInt(11) - 5;
                if (!level.hasChunkAt(new BlockPos(x, blockPosition().getY(), z))) continue;
                double ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                double height = net.minecraft.util.Mth.clamp(ground + .6 + random.nextDouble() * 1.6, getY() - 2, getY() + 2);
                Vec3 at = new Vec3(x + .5, height, z + .5);
                if (at.distanceToSqr(position()) > 1 && clear(level, at)) { flightTarget = at; break; }
            }
            targetUntil = tickCount + 60 + random.nextInt(40);
        }
        if (flightTarget != null) steer(level, flightTarget, level.isNight() ? .06 : .10, true);
        else steer(level, position().add(0, .5, 0), level.isNight() ? .045 : .07, true);
    }

    @Override
    @EntryPoint
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (level() instanceof ServerLevel server) Guarded.run("butterfly AI", getId(), () -> behave(server));
    }

    private void behave(ServerLevel level) {
        pollen.tickOutside();
        navigation.stop();
        boolean rain = rainSoon(level, blockPosition());
        if (rain && !avoidingRain) { shelterSearchAt = 0; flightTarget = null; detour = null; }
        avoidingRain = rain;
        boolean wet = level.isRainingAt(blockPosition());
        wetTicks = wet ? wetTicks + 1 : 0;
        if (wetTicks >= RAIN_TOLERANCE) { hurt(damageSources().drown(), 10); return; }
        if (rain || level.isNight()) {
            releaseFlower(level);
            if (!rain && Math.floorMod(tickCount, NIGHT_CYCLE) < NIGHT_ACTIVE) {
                entityData.set(RESTING, false); entityData.set(HANGING, false);
                wander(level); return; // Short night flights are for movement, never another pollination loop.
            }
            RestSpot current = restSpot(level, blockPosition());
            if (current == null) current = restSpot(level, blockPosition().above()); // fractional floor tops
            if (current != null && position().distanceToSqr(current.position()) < .035 && clear(level, current.position())) {
                entityData.set(RESTING, true);
                entityData.set(FLYING, false);
                entityData.set(HANGING, current.hanging());
                setPos(current.position().x, current.position().y, current.position().z);
                setNoGravity(true);
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            entityData.set(RESTING, false);
            entityData.set(HANGING, false);
            if (tickCount >= shelterSearchAt || shelter != null && restSpot(level, shelter) == null) {
                shelter = nearestShelter(level, blockPosition());
                shelterSearchAt = tickCount + (rain ? 10 : 40);
            }
            if (shelter != null) {
                steer(level, restSpot(level, shelter).position(), rain ? .18 : .075, true);
            } else wander(level); // Emergency flight continues searching instead of falling/crawling.
            return;
        }
        shelter = null;
        entityData.set(RESTING, false);
        entityData.set(HANGING, false);
        setNoGravity(true);
        if (flower != null && (!eligibleFlower(level, flower) || tickCount > flowerDeadline
                || !ButterflyVisits.acquire(level, flower, getId()))) releaseFlower(level);
        if (flower == null && tickCount >= flowerSearchAt) {
            BlockPos picked = findFlower(level);
            if (picked != null && ButterflyVisits.acquire(level, picked, getId())) {
                flower = picked; flowerDeadline = tickCount + 300;
            }
            flowerSearchAt = tickCount + 40;
        }
        if (flower == null) { entityData.set(FEEDING, false); wander(level); return; }
        Vec3 target = flowerTop(level, flower);
        if (position().distanceToSqr(target) < .065 && clear(level, target)) {
            entityData.set(FEEDING, true); entityData.set(FLYING, false);
            setPos(target.x, target.y, target.z); setDeltaMovement(Vec3.ZERO);
            if (hoverTicks % 20 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + .08, getZ(), 1, .04, .04, .04, 0);
            if (++hoverTicks >= 80) {
                if (level.getBlockState(flower).getBlock() instanceof io.github.slugblue.wildroots.plant.FieldPlantBlock)
                    PollinationService.butterflyVisit(level, pollen, flower);
                // Ordinary flowers provide a visual nectar visit, never a forged crop genome.
                recentFlowers.put(flower.asLong(), tickCount + 1200);
                if (recentFlowers.size() > 16) recentFlowers.remove(recentFlowers.keySet().iterator().next());
                releaseFlower(level); flowerSearchAt = tickCount + 60;
            }
        } else {
            entityData.set(FEEDING, false); hoverTicks = 0; steer(level, target, .12, true);
        }
    }

    private void releaseFlower(ServerLevel level) {
        ButterflyVisits.release(level, flower, getId());
        flower = null; hoverTicks = 0; entityData.set(FEEDING, false);
    }

    private boolean eligibleFlower(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || recentFlowers.getOrDefault(pos.asLong(), 0) > tickCount
                || !ButterflyVisits.available(level, pos, getId())) return false;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof io.github.slugblue.wildroots.plant.FieldPlantBlock) {
            if (io.github.slugblue.wildroots.plant.FieldPlantBlock.stage(state) != 4) return false;
            return io.github.slugblue.wildroots.core.safety.LoadedLevelAccess.blockEntity(level, pos,
                    io.github.slugblue.wildroots.plant.PlantBlockEntity.class)
                    .filter(be -> be.live(level).isPresent() && !be.hasForeignPollen() && (be.data().pending() == null
                            || be.data().pending().brush() == io.github.slugblue.wildroots.genetics.pollination.Brush.NONE))
                    .isPresent() && (!pollen.fresh() || pos.asLong() != pollen.source());
        }
        return state.is(net.minecraft.tags.BlockTags.FLOWERS)
                && (!state.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                    || state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
    }

    private Vec3 flowerTop(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF) && level.hasChunkAt(pos.above())
                && level.getBlockState(pos.above()).is(state.getBlock())) pos = pos.above();
        var shape = level.getBlockState(pos).getShape(level, pos);
        double top = shape.isEmpty() ? .7 : shape.max(net.minecraft.core.Direction.Axis.Y);
        return new Vec3(pos.getX() + .5, pos.getY() + top, pos.getZ() + .5);
    }

    private BlockPos findFlower(ServerLevel level) {
        BlockPos[] chosen = {null}; double[] best = {100};
        BloomIndex index = BloomIndex.peek(level);
        if (index != null) index.forEachNear(blockPosition(), 8, sp -> true, (sp, packed) -> {
            considerFlower(level, BlockPos.of(packed), chosen, best); return true;
        });
        // Bounded local scan: at most 405 cells every 40 ticks, all in loaded chunks.
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-4, -2, -4), blockPosition().offset(4, 2, 4))) {
            if (level.hasChunkAt(p) && level.getBlockState(p).is(net.minecraft.tags.BlockTags.FLOWERS))
                considerFlower(level, p, chosen, best);
        }
        return chosen[0];
    }

    private void considerFlower(ServerLevel level, BlockPos pos, BlockPos[] chosen, double[] best) {
        double distance = pos.distToCenterSqr(position());
        if (distance > 64 || !eligibleFlower(level, pos)) return;
        double score = distance + random.nextDouble() * 10;
        if (score < best[0]) { best[0] = score; chosen[0] = pos.immutable(); }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) releaseFlower(server);
        super.remove(reason);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    @EntryPoint
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        Guarded.run("butterfly save", getId(), () -> {
            tag.put("Pollen", pollen.save());
            tag.putInt("Variant", variant());
            if (wetTicks > 0) tag.putInt("WetTicks", wetTicks);
        });
    }

    @Override
    @EntryPoint
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Guarded.run("butterfly load", getId(), () -> {
            pollen.load(tag.getCompound("Pollen"));
            if (tag.contains("Variant", net.minecraft.nbt.Tag.TAG_INT)) entityData.set(VARIANT, Math.max(0, Math.min(2, tag.getInt("Variant"))));
            wetTicks = Math.max(0, Math.min(RAIN_TOLERANCE, tag.getInt("WetTicks")));
        });
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
