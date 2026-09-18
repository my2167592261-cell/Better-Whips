package com.betterwhips.client;

import com.betterwhips.physics.WhipBlockCollision;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class SeaRippleWhipIdlePhysics {
    private static final int POINTS = SeaRippleWhipGeometry.SEGMENT_COUNT + 1;
    private static final int WELDED_POINTS = 2;

    private static final double[] ROOT_MAX_BEND_RADIANS = {
            Math.toRadians(3.0D), Math.toRadians(4.0D), Math.toRadians(5.0D),
            Math.toRadians(6.5D), Math.toRadians(8.5D), Math.toRadians(11.5D),
            Math.toRadians(15.5D), Math.toRadians(21.0D)
    };
    private static final int SUBSTEPS = 5;
    private static final int SOLVER_ITERATIONS = 5;
    private static final int FINAL_LENGTH_PASSES = 3;
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double GRAVITY = -6.0D;
    private static final double TICK_VELOCITY_RETENTION = 0.976D;
    private static final double TICK_TANGENT_RETENTION = 0.955D;

    private static final double[] TAIL_RETENTION_SCALE = {0.994D, 1.004D, 0.999D, 1.007D, 0.996D};
    private static final double[] TAIL_TANGENT_SCALE = {1.004D, 0.993D, 1.000D, 0.989D, 1.006D};
    private static final double[] TAIL_GRAVITY_SCALE = {0.982D, 1.012D, 0.996D, 1.021D, 0.988D};
    private static final double[] TAIL_GUIDE_SCALE = {1.035D, 0.972D, 1.000D, 0.955D, 1.024D};

    private static final double GUIDE_ACCEL_ROOT = 62.0D;
    private static final double GUIDE_ACCEL_TIP = 28.0D;

    private static final double GUIDE_DAMPING_ROOT = 14.0D;
    private static final double GUIDE_DAMPING_TIP = 9.5D;
    private static final double GUIDE_MAX_ACCEL = 52.0D;
    private static final double GUIDE_MAX_DAMPING_ACCEL = 95.0D;

    private static final double ATTACK_GUIDE_ACCEL_ROOT = 172.0D;
    private static final double ATTACK_GUIDE_ACCEL_TIP = 11.0D;
    private static final double ATTACK_GUIDE_DAMPING_ROOT = 17.5D;
    private static final double ATTACK_GUIDE_DAMPING_TIP = 6.0D;
    private static final double ATTACK_GUIDE_MAX_ACCEL = 145.0D;
    private static final double ATTACK_GRAVITY_SCALE = 0.42D;
    private static final double ATTACK_TICK_VELOCITY_RETENTION = 0.989D;
    private static final double ATTACK_TICK_TANGENT_RETENTION = 0.976D;

    private static final double ATTACK_PROJECTION_ABSORPTION_ROOT = 0.94D;
    private static final double ATTACK_PROJECTION_ABSORPTION_TIP = 0.84D;

    private static final double PROJECTION_VELOCITY_ABSORPTION_ROOT = 0.94D;
    private static final double PROJECTION_VELOCITY_ABSORPTION_TIP = 0.82D;

    private static final double IDLE_MAX_POINT_SPEED = 8.0D;
    private static final double ATTACK_MAX_POINT_SPEED = 55.0D;
    private static final double COLLISION_RADIUS = 0.022D;
    private static final double MAX_CONTACT_CORRECTION = 0.24D;
    private static final double RESET_ROOT_DISTANCE_SQR = 6.0D * 6.0D;
    private static final int MAX_CATCHUP_TICKS = 3;

    private static final int ROOT_TRANSLATION_FULL_CARRY_END = 10;
    private static final double ROOT_TRANSLATION_TIP_CARRY = 0.12D;
    private static final int ROOT_ROTATION_FULL_CARRY_END = 8;
    private static final int ROOT_ROTATION_CARRY_END = 18;
    private static final double ROOT_ROTATION_MAX_RADIANS = Math.toRadians(35.0D);
    private static final long KEEP_STATE_TICKS = 60L;
    private static final double EPSILON = 1.0E-8D;
    private static final double[] COLLIDER_RADII = colliderRadii();

    private static final Map<Key, TailState> STATES = new HashMap<>();
    private static ClientLevel cacheLevel;
    private static long cacheTick = Long.MIN_VALUE;
    private static WhipBlockCollision.TickCache collisionCache;

    private SeaRippleWhipIdlePhysics() {}

    static void clear() {
        STATES.clear();
        cacheLevel = null;
        cacheTick = Long.MIN_VALUE;
        collisionCache = null;
    }

    static void prune(ClientLevel level) {
        if (level == null) {
            clear();
            return;
        }
        long now = level.getGameTime();
        STATES.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick > KEEP_STATE_TICKS);
        if (cacheLevel != level || cacheTick != now) {
            cacheLevel = level;
            cacheTick = now;
            collisionCache = new WhipBlockCollision.TickCache();
        }
    }

    static Vec3[] sample(Player player, HumanoidArm arm, int tail, Vec3 root, Vec3 rootDirection,
                         Vec3[] fallbackShape, float partialTick, double attackDrive) {
        if (!(player.level() instanceof ClientLevel level) || !finite(root)) return fallbackShape;
        long tick = level.getGameTime();
        TailState state = STATES.computeIfAbsent(new Key(player.getUUID(), arm, tail), unused -> new TailState());
        state.lastSeenTick = tick;

        Vec3 direction = unit(rootDirection, fallbackDirection(fallbackShape));
        if (!finite(direction)) direction = new Vec3(0.0D, -0.20D, 0.98D).normalize();

        Vec3[] guide = lengthPreservingGuide(root, direction, fallbackShape);

        boolean teleported = state.initialized && finite(state.lastRoot)
                && state.lastRoot.distanceToSqr(root) > RESET_ROOT_DISTANCE_SQR;
        if (!state.initialized || teleported || tick < state.lastTick || tick - state.lastTick > MAX_CATCHUP_TICKS) {
            seed(state, root, direction, guide, tick);
        } else if (tick > state.lastTick) {

            transportRootFrame(state, root, direction);
            int steps = (int)Math.min(MAX_CATCHUP_TICKS, tick - state.lastTick);
            double drive = Math.max(0.0D, Math.min(1.0D, attackDrive));
            for (int step = 0; step < steps; ++step) {
                System.arraycopy(state.points, 0, state.renderPrevious, 0, POINTS);
                simulateTick(level, state, tail, root, direction, guide, tick, drive);
            }
            state.lastTick = tick;
            state.simulatedRoot = root;
            state.simulatedDirection = direction;
        }

        state.lastRoot = root;
        double alpha = Math.max(0.0D, Math.min(1.0D, partialTick));
        Vec3[] result = new Vec3[POINTS];
        Vec3 renderRootDelta = finite(state.simulatedRoot)
                ? root.subtract(state.simulatedRoot) : Vec3.ZERO;
        for (int i = 0; i < POINTS; ++i) {
            Vec3 a = state.renderPrevious[i] == null ? state.points[i] : state.renderPrevious[i];
            result[i] = a.lerp(state.points[i], alpha);
            if (i >= WELDED_POINTS && renderRootDelta.lengthSqr() > EPSILON) {
                double u = (i - 1) / (double)Math.max(1, POINTS - 2);
                double rootCarry = 1.0D - smoothstep(u);
                result[i] = result[i].add(renderRootDelta.scale(rootCarry));
            }
        }
        result[0] = root;
        result[1] = root.add(direction.scale(SeaRippleWhipGeometry.REST_LENGTHS[0]));
        return result;
    }

    private static void seed(TailState state, Vec3 root, Vec3 direction, Vec3[] shape, long tick) {
        state.points[0] = root;
        state.points[1] = root.add(direction.scale(SeaRippleWhipGeometry.REST_LENGTHS[0]));
        Vec3 previousDirection = direction;
        for (int i = 2; i < POINTS; ++i) {
            Vec3 desired = shape != null && shape.length == POINTS
                    ? shape[i].subtract(shape[i - 1]) : Vec3.ZERO;
            if (!finite(desired) || desired.lengthSqr() < EPSILON) {
                double u = i / (double)(POINTS - 1);
                double down = smoothstep(u) * 0.82D;
                desired = previousDirection.scale(1.0D - down * 0.58D).add(0.0D, -down, 0.0D);
            }
            desired = unit(desired, previousDirection);
            state.points[i] = state.points[i - 1]
                    .add(desired.scale(SeaRippleWhipGeometry.REST_LENGTHS[i - 1]));
            previousDirection = desired;
        }

        for (int i = 0; i < POINTS; ++i) {
            state.previous[i] = state.points[i];
            state.renderPrevious[i] = state.points[i];
        }
        state.initialized = true;
        state.lastTick = tick;
        state.lastRoot = root;
        state.simulatedRoot = root;
        state.simulatedDirection = direction;
    }

    private static void transportRootFrame(TailState state, Vec3 root, Vec3 direction) {
        if (!state.initialized || !finite(state.simulatedRoot)) return;

        Vec3 rootDelta = root.subtract(state.simulatedRoot);
        if (finite(rootDelta) && rootDelta.lengthSqr() > EPSILON) {
            for (int i = WELDED_POINTS; i < POINTS; ++i) {
                double carry;
                if (i <= ROOT_TRANSLATION_FULL_CARRY_END) {
                    carry = 1.0D;
                } else {
                    double u = (i - ROOT_TRANSLATION_FULL_CARRY_END)
                            / (double)Math.max(1, POINTS - 1 - ROOT_TRANSLATION_FULL_CARRY_END);
                    carry = 1.0D + (ROOT_TRANSLATION_TIP_CARRY - 1.0D) * smoothstep(u);
                }
                Vec3 shift = rootDelta.scale(carry);
                state.points[i] = state.points[i].add(shift);
                state.previous[i] = state.previous[i].add(shift);
                if (state.renderPrevious[i] != null) {
                    state.renderPrevious[i] = state.renderPrevious[i].add(shift);
                }
            }
        }

        Vec3 oldDirection = unit(state.simulatedDirection, direction);
        Vec3 newDirection = unit(direction, oldDirection);
        double dot = Math.max(-1.0D, Math.min(1.0D, oldDirection.dot(newDirection)));
        double angle = Math.acos(dot);
        Vec3 axis = oldDirection.cross(newDirection);
        double axisLengthSqr = axis.lengthSqr();
        if (Double.isFinite(angle) && angle > 1.0E-6D && axisLengthSqr > EPSILON) {
            axis = axis.scale(1.0D / Math.sqrt(axisLengthSqr));
            angle = Math.min(angle, ROOT_ROTATION_MAX_RADIANS);
            for (int i = WELDED_POINTS; i < Math.min(POINTS, ROOT_ROTATION_CARRY_END + 1); ++i) {
                double carry;
                if (i <= ROOT_ROTATION_FULL_CARRY_END) {
                    carry = 1.0D;
                } else {
                    double u = (i - ROOT_ROTATION_FULL_CARRY_END)
                            / (double)Math.max(1, ROOT_ROTATION_CARRY_END - ROOT_ROTATION_FULL_CARRY_END);
                    carry = 1.0D - smoothstep(u);
                }
                double localAngle = angle * carry;
                state.points[i] = rotateAroundAxis(state.points[i].subtract(root), axis, localAngle).add(root);
                state.previous[i] = rotateAroundAxis(state.previous[i].subtract(root), axis, localAngle).add(root);
                if (state.renderPrevious[i] != null) {
                    state.renderPrevious[i] = rotateAroundAxis(
                            state.renderPrevious[i].subtract(root), axis, localAngle).add(root);
                }
            }
        }

        state.points[0] = root;
        state.points[1] = root.add(newDirection.scale(SeaRippleWhipGeometry.REST_LENGTHS[0]));
        state.previous[0] = state.points[0];
        state.previous[1] = state.points[1];
        state.renderPrevious[0] = state.points[0];
        state.renderPrevious[1] = state.points[1];
    }

    private static Vec3 rotateAroundAxis(Vec3 value, Vec3 axis, double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return value.scale(cosine)
                .add(axis.cross(value).scale(sine))
                .add(axis.scale(axis.dot(value) * (1.0D - cosine)));
    }

    private static void simulateTick(ClientLevel level, TailState state, int tail, Vec3 root,
                                     Vec3 direction, Vec3[] guide, long tick, double attackDrive) {
        WhipBlockCollision.TickCache cache = collisionCache(level, tick);
        double dt = TICK_SECONDS / SUBSTEPS;
        double dtSqr = dt * dt;
        int profile = Math.max(0, Math.min(4, tail));
        double drive = Math.max(0.0D, Math.min(1.0D, attackDrive));
        double idleRetention = TICK_VELOCITY_RETENTION * TAIL_RETENTION_SCALE[profile];
        double attackRetention = ATTACK_TICK_VELOCITY_RETENTION * TAIL_RETENTION_SCALE[profile];
        double tickRetention = Math.max(0.90D, Math.min(0.9995D,
                idleRetention + (attackRetention - idleRetention) * drive));
        double idleTangentRetention = TICK_TANGENT_RETENTION * TAIL_TANGENT_SCALE[profile];
        double attackTangentRetention = ATTACK_TICK_TANGENT_RETENTION * TAIL_TANGENT_SCALE[profile];
        double tickTangentRetention = Math.max(0.88D, Math.min(0.999D,
                idleTangentRetention + (attackTangentRetention - idleTangentRetention) * drive));
        double retention = Math.pow(tickRetention, 1.0D / SUBSTEPS);
        double tangentRetention = Math.pow(tickTangentRetention, 1.0D / SUBSTEPS);
        double gravityScale = 1.0D + (ATTACK_GRAVITY_SCALE - 1.0D) * drive;
        double gravity = GRAVITY * TAIL_GRAVITY_SCALE[profile] * gravityScale;
        double guideScale = TAIL_GUIDE_SCALE[profile];
        Vec3 welded = root.add(direction.scale(SeaRippleWhipGeometry.REST_LENGTHS[0]));

        for (int substep = 0; substep < SUBSTEPS; ++substep) {
            pin(state, root, welded);
            Vec3[] before = copy(state.points);
            for (int i = WELDED_POINTS; i < POINTS; ++i) {
                Vec3 current = state.points[i];
                Vec3 old = state.previous[i];
                Vec3 velocity = current.subtract(old).scale(retention);

                double maxPointSpeed = IDLE_MAX_POINT_SPEED
                        + (ATTACK_MAX_POINT_SPEED - IDLE_MAX_POINT_SPEED) * drive;
                double maxStep = maxPointSpeed * dt;
                double velocityLength = velocity.length();
                if (Double.isFinite(velocityLength) && velocityLength > maxStep && velocityLength > EPSILON) {
                    velocity = velocity.scale(maxStep / velocityLength);
                }
                double u = (i - WELDED_POINTS) / (double)Math.max(1, POINTS - 1 - WELDED_POINTS);
                double shape = smoothstep(u);
                double idleGuideAccel = GUIDE_ACCEL_ROOT
                        + (GUIDE_ACCEL_TIP - GUIDE_ACCEL_ROOT) * shape;
                double attackGuideAccel = ATTACK_GUIDE_ACCEL_ROOT
                        + (ATTACK_GUIDE_ACCEL_TIP - ATTACK_GUIDE_ACCEL_ROOT)
                        * Math.pow(shape, 0.72D);
                double guideAccel = (idleGuideAccel
                        + (attackGuideAccel - idleGuideAccel) * drive) * guideScale;
                double idleGuideDamping = GUIDE_DAMPING_ROOT
                        + (GUIDE_DAMPING_TIP - GUIDE_DAMPING_ROOT) * shape;
                double attackGuideDamping = ATTACK_GUIDE_DAMPING_ROOT
                        + (ATTACK_GUIDE_DAMPING_TIP - ATTACK_GUIDE_DAMPING_ROOT) * shape;
                double guideDamping = idleGuideDamping
                        + (attackGuideDamping - idleGuideDamping) * drive;
                double maxGuideAccel = GUIDE_MAX_ACCEL
                        + (ATTACK_GUIDE_MAX_ACCEL - GUIDE_MAX_ACCEL) * drive;
                Vec3 guideError = guide[i].subtract(current);
                Vec3 attraction = guideError.scale(guideAccel);
                double attractionLength = attraction.length();
                if (Double.isFinite(attractionLength) && attractionLength > maxGuideAccel) {
                    attraction = attraction.scale(maxGuideAccel / attractionLength);
                }

                Vec3 guideDampingAcceleration = Vec3.ZERO;
                double errorLengthSqr = guideError.lengthSqr();
                if (Double.isFinite(errorLengthSqr) && errorLengthSqr > EPSILON) {
                    Vec3 guideAxis = guideError.scale(1.0D / Math.sqrt(errorLengthSqr));
                    Vec3 physicalVelocity = current.subtract(old).scale(1.0D / dt);
                    double radialSpeed = physicalVelocity.dot(guideAxis);
                    guideDampingAcceleration = guideAxis.scale(-radialSpeed * guideDamping);
                    double dampingLength = guideDampingAcceleration.length();
                    if (Double.isFinite(dampingLength) && dampingLength > GUIDE_MAX_DAMPING_ACCEL) {
                        guideDampingAcceleration = guideDampingAcceleration
                                .scale(GUIDE_MAX_DAMPING_ACCEL / dampingLength);
                    }
                }

                Vec3 acceleration = attraction.add(guideDampingAcceleration).add(0.0D, gravity, 0.0D);
                state.previous[i] = current;
                state.points[i] = current.add(velocity).add(acceleration.scale(dtSqr));
            }
            Vec3[] predicted = copy(state.points);
            pin(state, root, welded);
            for (int pass = 0; pass < 2; ++pass) {
                solveLengths(state.points, root, welded);
                solveRootBend(state.points, root, welded);
            }

            WhipBlockCollision.SegmentEnvironment[] broadphase = WhipBlockCollision.buildBroadphase(
                    level, before, state.points, COLLIDER_RADII, 1.0D, cache);
            for (int iteration = 0; iteration < SOLVER_ITERATIONS; ++iteration) {
                solveLengths(state.points, root, welded);
                solveRootBend(state.points, root, welded);
                if (iteration == 2) solveContacts(state, before, broadphase, tangentRetention);
            }
            solveContacts(state, before, broadphase, tangentRetention);
            for (int pass = 0; pass < FINAL_LENGTH_PASSES; ++pass) {
                solveLengths(state.points, root, welded);
                solveRootBend(state.points, root, welded);
            }
            pin(state, root, welded);
            absorbProjectionVelocity(state, predicted, drive);
            sanitize(state, root, direction);
        }
    }

    private static void absorbProjectionVelocity(TailState state, Vec3[] predicted, double attackDrive) {
        if (predicted == null || predicted.length != POINTS) return;
        double drive = Math.max(0.0D, Math.min(1.0D, attackDrive));
        for (int i = WELDED_POINTS; i < POINTS; ++i) {
            Vec3 correction = state.points[i].subtract(predicted[i]);
            if (!finite(correction)) continue;
            double u = (i - WELDED_POINTS) / (double)Math.max(1, POINTS - 1 - WELDED_POINTS);
            double shape = smoothstep(u);
            double idleAbsorption = PROJECTION_VELOCITY_ABSORPTION_ROOT
                    + (PROJECTION_VELOCITY_ABSORPTION_TIP - PROJECTION_VELOCITY_ABSORPTION_ROOT)
                    * shape;
            double attackAbsorption = ATTACK_PROJECTION_ABSORPTION_ROOT
                    + (ATTACK_PROJECTION_ABSORPTION_TIP - ATTACK_PROJECTION_ABSORPTION_ROOT)
                    * shape;
            double absorption = idleAbsorption + (attackAbsorption - idleAbsorption) * drive;
            state.previous[i] = state.previous[i].add(correction.scale(absorption));
        }
    }

    private static void solveLengths(Vec3[] points, Vec3 root, Vec3 welded) {
        points[0] = root;
        points[1] = welded;
        for (int segment = 1; segment < SeaRippleWhipGeometry.SEGMENT_COUNT; ++segment) {
            int a = segment, b = segment + 1;
            Vec3 delta = points[b].subtract(points[a]);
            double length = delta.length();
            double rest = SeaRippleWhipGeometry.REST_LENGTHS[segment];
            if (!Double.isFinite(length) || length < EPSILON) continue;
            double error = (length - rest) / length;
            if (a < WELDED_POINTS) {
                points[b] = points[b].subtract(delta.scale(error));
            } else {
                Vec3 correction = delta.scale(error * 0.5D);
                points[a] = points[a].add(correction);
                points[b] = points[b].subtract(correction);
            }
        }
        points[0] = root;
        points[1] = welded;
    }

    private static void solveRootBend(Vec3[] points, Vec3 root, Vec3 welded) {
        points[0] = root;
        points[1] = welded;
        Vec3 previousDirection = unit(welded.subtract(root), new Vec3(0.0D, 0.0D, 1.0D));
        int count = Math.min(ROOT_MAX_BEND_RADIANS.length, SeaRippleWhipGeometry.SEGMENT_COUNT - 1);
        for (int local = 0; local < count; ++local) {
            int a = local + 1;
            int b = a + 1;
            Vec3 delta = points[b].subtract(points[a]);
            if (!finite(delta) || delta.lengthSqr() < EPSILON) continue;
            Vec3 currentDirection = delta.normalize();
            double cosine = Math.max(-1.0D, Math.min(1.0D, previousDirection.dot(currentDirection)));
            double angle = Math.acos(cosine);
            double maxAngle = ROOT_MAX_BEND_RADIANS[local];
            Vec3 limited = currentDirection;
            if (Double.isFinite(angle) && angle > maxAngle && angle > 1.0E-7D) {
                double blend = maxAngle / angle;
                limited = unit(previousDirection.lerp(currentDirection, blend), previousDirection);
            }

            points[b] = points[a].add(limited.scale(SeaRippleWhipGeometry.REST_LENGTHS[a]));
            previousDirection = limited;
        }
        points[0] = root;
        points[1] = welded;
    }

    private static void solveContacts(TailState state, Vec3[] before,
                                      WhipBlockCollision.SegmentEnvironment[] broadphase,
                                      double tangentRetention) {
        int segments = Math.min(SeaRippleWhipGeometry.SEGMENT_COUNT, broadphase.length);
        for (int segment = 0; segment < segments; ++segment) {
            WhipBlockCollision.SegmentContact contact = WhipBlockCollision.findContact(
                    broadphase[segment], before[segment], before[segment + 1],
                    state.points[segment], state.points[segment + 1],
                    COLLIDER_RADII[segment], tangentRetention);
            if (contact == null || !finite(contact.correction())) continue;
            Vec3 correction = contact.correction();
            double length = correction.length();
            if (length > MAX_CONTACT_CORRECTION) correction = correction.scale(MAX_CONTACT_CORRECTION / length);
            double s = Math.max(0.0D, Math.min(1.0D, contact.sample()));
            double wa = segment < WELDED_POINTS ? 0.0D : 1.0D;
            double wb = segment + 1 < WELDED_POINTS ? 0.0D : 1.0D;
            double a = 1.0D - s, b = s;
            double denominator = wa * a * a + wb * b * b;
            if (denominator < EPSILON) continue;
            if (wa > 0.0D) state.points[segment] = state.points[segment]
                    .add(correction.scale(wa * a / denominator));
            if (wb > 0.0D) state.points[segment + 1] = state.points[segment + 1]
                    .add(correction.scale(wb * b / denominator));
        }
    }

    private static void sanitize(TailState state, Vec3 root, Vec3 direction) {
        Vec3 welded = root.add(direction.scale(SeaRippleWhipGeometry.REST_LENGTHS[0]));
        if (!finite(state.points[0]) || !finite(state.points[POINTS - 1])
                || state.points[POINTS - 1].distanceToSqr(root) > 36.0D) {
            seed(state, root, direction, null, state.lastTick);
            return;
        }
        state.points[0] = root;
        state.points[1] = welded;
        state.previous[0] = root;
        state.previous[1] = welded;
    }

    private static void pin(TailState state, Vec3 root, Vec3 welded) {
        state.points[0] = root;
        state.points[1] = welded;
        state.previous[0] = root;
        state.previous[1] = welded;
    }

    private static WhipBlockCollision.TickCache collisionCache(ClientLevel level, long tick) {
        if (cacheLevel != level || cacheTick != tick || collisionCache == null) {
            cacheLevel = level;
            cacheTick = tick;
            collisionCache = new WhipBlockCollision.TickCache();
        }
        return collisionCache;
    }

    private static Vec3[] lengthPreservingGuide(Vec3 root, Vec3 rootDirection, Vec3[] shape) {
        Vec3[] guide = new Vec3[POINTS];
        Vec3 previousDirection = unit(rootDirection, fallbackDirection(shape));
        guide[0] = root;
        guide[1] = root.add(previousDirection.scale(SeaRippleWhipGeometry.REST_LENGTHS[0]));
        for (int i = 1; i < SeaRippleWhipGeometry.SEGMENT_COUNT; ++i) {
            Vec3 desired = shape != null && shape.length == POINTS
                    ? shape[i + 1].subtract(shape[i]) : Vec3.ZERO;
            desired = unit(desired, previousDirection);

            if (i < 10) {
                double blend = Math.pow(smoothstep(i / 10.0D), 2.0D);
                desired = unit(previousDirection.scale(1.0D - blend).add(desired.scale(blend)),
                        previousDirection);
            }
            guide[i + 1] = guide[i].add(desired.scale(SeaRippleWhipGeometry.REST_LENGTHS[i]));
            previousDirection = desired;
        }
        return guide;
    }

    private static Vec3 fallbackDirection(Vec3[] shape) {
        if (shape != null && shape.length > 1) {
            Vec3 direction = shape[1].subtract(shape[0]);
            if (finite(direction) && direction.lengthSqr() >= EPSILON) return direction.normalize();
        }
        return new Vec3(0.0D, -0.2D, 0.98D).normalize();
    }

    private static Vec3 unit(Vec3 value, Vec3 fallback) {
        return finite(value) && value.lengthSqr() >= EPSILON ? value.normalize() : fallback;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Vec3[] copy(Vec3[] source) {
        return Arrays.copyOf(source, source.length);
    }

    private static double smoothstep(double t) {
        t = Math.max(0.0D, Math.min(1.0D, t));
        return t * t * (3.0D - 2.0D * t);
    }

    private static double[] colliderRadii() {
        double[] result = new double[SeaRippleWhipGeometry.SEGMENT_COUNT];
        for (int i = 0; i < result.length; ++i) {
            double u = i / (double)Math.max(1, result.length - 1);
            result[i] = COLLISION_RADIUS * (1.0D - 0.30D * u);
        }
        return result;
    }

    private record Key(UUID player, HumanoidArm arm, int tail) {}

    private static final class TailState {
        final Vec3[] points = new Vec3[POINTS];
        final Vec3[] previous = new Vec3[POINTS];
        final Vec3[] renderPrevious = new Vec3[POINTS];
        Vec3 lastRoot;
        Vec3 simulatedRoot;
        Vec3 simulatedDirection;
        long lastTick = Long.MIN_VALUE;
        long lastSeenTick = Long.MIN_VALUE;
        boolean initialized;
    }
}
