package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class WhipShockwaveEffects {
    private static final int SURFACE_SCAN_BASE_DOWN = 12;
    private static final int SURFACE_SCAN_EXTRA_PER_TIER = 2;
    private static final int SURFACE_SCAN_UP = 4;
    private static final double CAMERA_CULL_DISTANCE = 256.0D;
    private static final double CAMERA_CULL_DISTANCE_SQR = CAMERA_CULL_DISTANCE * CAMERA_CULL_DISTANCE;
    private static final float CUBE_INSET = 0.015F;

    private static final float RIPPLE_APEX = 0.75F;
    private static final float OUTWARD_WAVE_TICKS_PER_BLOCK = 0.30F;
    private static final float BASE_PIECE_LIFETIME = 8.0F;
    private static final double ADJACENT_X_MULTIPLIER = 16.0D;

    private static final int BUILD_COLUMNS_PER_CLIENT_TICK = 4096;

    private static final List<LandingBurst> ACTIVE_BURSTS = new ArrayList<>();
    private static ResourceKey<Level> activeDimension;

    private WhipShockwaveEffects() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        if (activeDimension != level.dimension()) {
            clear();
            activeDimension = level.dimension();
        }

        for (LandingBurst burst : ACTIVE_BURSTS) {
            burst.buildAhead(level, BUILD_COLUMNS_PER_CLIENT_TICK);
        }

        long gameTime = level.getGameTime();
        ACTIVE_BURSTS.removeIf(burst -> gameTime - burst.startGameTime > burst.maxLifetime + 2L);
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE_BURSTS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = level.getGameTime();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        Map<BlockState, BlockSprites> spriteCache = new IdentityHashMap<>();
        FaceShades faceShades = new FaceShades(
                shadeByte(level.getShade(Direction.UP, true)),
                shadeByte(level.getShade(Direction.DOWN, true)),
                shadeByte(level.getShade(Direction.NORTH, true)),
                shadeByte(level.getShade(Direction.SOUTH, true)),
                shadeByte(level.getShade(Direction.WEST, true)),
                shadeByte(level.getShade(Direction.EAST, true))
        );

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(RenderType.solid());
        boolean emitted = false;

        for (LandingBurst burst : ACTIVE_BURSTS) {
            float burstAge = (float) (gameTime - burst.startGameTime) + partialTick;
            if (burstAge < 0.0F) continue;

            int firstBucket = Math.max(0, Mth.floor(burstAge - burst.pieceLifetime) - 1);
            int lastBucket = Math.min(burst.buckets.size() - 1, Mth.floor(burstAge) + 1);
            for (int bucketIndex = firstBucket; bucketIndex <= lastBucket; ++bucketIndex) {
                for (DebrisPiece piece : burst.buckets.get(bucketIndex)) {
                    float localAge = burstAge - piece.startDelay;
                    if (localAge < 0.0F || localAge >= burst.pieceLifetime) continue;
                    float t = Mth.clamp(localAge / burst.pieceLifetime, 0.0F, 1.0F);

                    float verticalOffset = Mth.sin(Mth.PI * t) * RIPPLE_APEX;
                    if (verticalOffset <= 0.0125F) continue;

                    double cx = piece.pos.getX() + 0.5D;
                    double cy = piece.pos.getY() + 0.5D + verticalOffset;
                    double cz = piece.pos.getZ() + 0.5D;
                    double dx = cx - cameraPos.x;
                    double dy = cy - cameraPos.y;
                    double dz = cz - cameraPos.z;
                    if (dx * dx + dy * dy + dz * dz > CAMERA_CULL_DISTANCE_SQR) {
                        continue;
                    }

                    BlockSprites sprites = spriteCache.computeIfAbsent(piece.state,
                            state -> resolveSprites(minecraft, state));
                    emitCube(poseStack.last(), buffer, cameraPos, sprites, piece, faceShades, verticalOffset);
                    if (piece.coverState != null) {
                        BlockSprites coverSprites = spriteCache.computeIfAbsent(piece.coverState,
                                state -> resolveSprites(minecraft, state));
                        emitSnowCover(poseStack.last(), buffer, cameraPos, coverSprites, piece, faceShades, verticalOffset);
                    }
                    emitted = true;
                }
            }
        }

        if (emitted) {

            buffers.endBatch(RenderType.solid());
        }
    }

    public static void spawnWhipShockwave(ClientLevel level, double x, double y, double z, long seed) {
        if (level == null) {
            return;
        }
        final int tier = 5;
        final float rippleScale = 0.50F;
        final float pieceLifetime = BASE_PIECE_LIFETIME * rippleScale;
        final double adjacentBlocks = tier * ADJACENT_X_MULTIPLIER;
        final double half = 0.0D;
        final double c = 1.0D;
        final double sn = 0.0D;
        final double broadHalf = adjacentBlocks + 1.0D;
        final int minX = Mth.floor(x - broadHalf);
        final int maxX = Mth.floor(x + broadHalf);
        final int minZ = Mth.floor(z - broadHalf);
        final int maxZ = Mth.floor(z + broadHalf);
        final long seedBase = mix64(seed);
        final int maxStartBucket = Mth.ceil((float)(adjacentBlocks * OUTWARD_WAVE_TICKS_PER_BLOCK)) + 2;
        List<List<DebrisPiece>> buckets = new ArrayList<>(maxStartBucket + 1);
        for (int i = 0; i <= maxStartBucket; ++i) {
            buckets.add(new ArrayList<>());
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z));
        BlockPos skyProbe = BlockPos.containing(x, y + 1.0D, z);
        boolean preferHeightmap = level.canSeeSky(skyProbe) || Math.abs(surfaceY - y) <= 3.0D;
        int maxLifetime = Mth.ceil((float)(adjacentBlocks * OUTWARD_WAVE_TICKS_PER_BLOCK)
                + pieceLifetime) + 3;

        ACTIVE_BURSTS.add(new LandingBurst(
                level.getGameTime(), maxLifetime, pieceLifetime, buckets, tier,
                x, y, z, half, c, sn, adjacentBlocks,
                minX, maxX, minZ, maxZ, seedBase, preferHeightmap
        ));
    }

    private static SurfaceSample findSurface(ClientLevel level, int x, int z, double landingY,
                                             int tier, boolean preferHeightmap,
                                             BlockPos.MutableBlockPos probe,
                                             BlockPos.MutableBlockPos above) {

        probe.set(x, Mth.floor(landingY), z);
        if (!level.hasChunkAt(probe)) {
            return null;
        }
        if (preferHeightmap) {
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (surfaceY >= level.getMinBuildHeight() && surfaceY < level.getMaxBuildHeight() - 1) {
                probe.set(x, surfaceY, z);
                BlockState state = level.getBlockState(probe);
                above.set(x, surfaceY + 1, z);
                BlockState aboveState = level.getBlockState(above);
                if (isRenderableSurface(state, aboveState)) {
                    return new SurfaceSample(probe.immutable(), state, captureSurfaceCover(aboveState));
                }
            }
        }

        return findNearestSurface(level, x, z, landingY, tier, probe, above);
    }

    private static SurfaceSample findNearestSurface(ClientLevel level, int x, int z, double landingY,
                                                     int tier, BlockPos.MutableBlockPos probe,
                                                     BlockPos.MutableBlockPos above) {
        int centerY = Mth.floor(landingY) - 1;
        int minY = Math.max(level.getMinBuildHeight(),
                centerY - SURFACE_SCAN_BASE_DOWN - tier * SURFACE_SCAN_EXTRA_PER_TIER);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, centerY + SURFACE_SCAN_UP + tier);
        if (maxY < minY) return null;

        SurfaceSample best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int y = minY; y <= maxY; ++y) {
            probe.set(x, y, z);
            BlockState state = level.getBlockState(probe);

            above.set(x, y + 1, z);
            BlockState aboveState = level.getBlockState(above);
            if (!isRenderableSurface(state, aboveState)) continue;

            double surfaceY = y + 1.0D;
            double distance = Math.abs(surfaceY - landingY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new SurfaceSample(probe.immutable(), state, captureSurfaceCover(aboveState));
            }
        }
        return best;
    }

    private static boolean isRenderableSurface(BlockState state, BlockState aboveState) {
        return !state.isAir() && state.canOcclude() && !aboveState.canOcclude();
    }

    private static BlockState captureSurfaceCover(BlockState aboveState) {
        return aboveState.getBlock() instanceof SnowLayerBlock ? aboveState : null;
    }

    private static BlockSprites resolveSprites(Minecraft minecraft, BlockState state) {
        TextureAtlasSprite fallback = minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        return new BlockSprites(
                resolveFace(model, state, Direction.UP, fallback),
                resolveFace(model, state, Direction.DOWN, fallback),
                resolveFace(model, state, Direction.NORTH, fallback),
                resolveFace(model, state, Direction.SOUTH, fallback),
                resolveFace(model, state, Direction.WEST, fallback),
                resolveFace(model, state, Direction.EAST, fallback)
        );
    }

    private static int resolveTopTintIndex(Minecraft minecraft, BlockState state) {
        TextureAtlasSprite fallback = minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        return resolveFace(model, state, Direction.UP, fallback).tintIndex;
    }

    private static FaceSprite resolveFace(BakedModel model, BlockState state, Direction direction,
                                          TextureAtlasSprite fallback) {
        List<BakedQuad> quads = model.getQuads(
                state, direction, RandomSource.create(0L), ModelData.EMPTY, null);
        if (quads.isEmpty()) {
            return new FaceSprite(fallback, -1);
        }

        if (direction != Direction.UP) {
            for (BakedQuad candidate : quads) {
                if (!candidate.isTinted()) {
                    return new FaceSprite(candidate.getSprite(), -1);
                }
            }
        }
        BakedQuad quad = quads.get(0);
        return new FaceSprite(quad.getSprite(), quad.isTinted() ? quad.getTintIndex() : -1);
    }

    private static int sampleTint(Minecraft minecraft, ClientLevel level, SurfaceSample sample,
                                  int tintIndex) {
        if (tintIndex < 0) {
            return -1;
        }

        int tint = minecraft.getBlockColors().getColor(sample.state, level, sample.pos, tintIndex);

        return tint == -1 ? -1 : tint;
    }

    private static void emitCube(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                 BlockSprites sprites, DebrisPiece piece, FaceShades shades,
                                 float verticalOffset) {
        float x0 = (float) (piece.pos.getX() + CUBE_INSET - camera.x);
        float x1 = (float) (piece.pos.getX() + 1.0D - CUBE_INSET - camera.x);
        float y0 = (float) (piece.pos.getY() + verticalOffset + CUBE_INSET - camera.y);
        float y1 = (float) (piece.pos.getY() + verticalOffset + 1.0D - CUBE_INSET - camera.y);
        float z0 = (float) (piece.pos.getZ() + CUBE_INSET - camera.z);
        float z1 = (float) (piece.pos.getZ() + 1.0D - CUBE_INSET - camera.z);
        int light = piece.packedLight;

        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.UP, sprites.up, piece, shades.up, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.DOWN, sprites.down, piece, shades.down, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.WEST, sprites.west, piece, shades.west, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.EAST, sprites.east, piece, shades.east, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.NORTH, sprites.north, piece, shades.north, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.SOUTH, sprites.south, piece, shades.south, light);
    }

    private static void emitSnowCover(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                      BlockSprites sprites, DebrisPiece piece, FaceShades shades,
                                      float verticalOffset) {
        if (!(piece.coverState.getBlock() instanceof SnowLayerBlock)
                || !piece.coverState.hasProperty(SnowLayerBlock.LAYERS)) {
            return;
        }

        int layers = Mth.clamp(piece.coverState.getValue(SnowLayerBlock.LAYERS), 1, 8);
        float height = layers / 8.0F;
        float inset = 0.003F;
        float x0 = (float) (piece.pos.getX() + inset - camera.x);
        float x1 = (float) (piece.pos.getX() + 1.0D - inset - camera.x);
        float y0 = (float) (piece.pos.getY() + 1.0D + verticalOffset - camera.y);
        float y1 = y0 + Math.max(0.01F, height - inset);
        float z0 = (float) (piece.pos.getZ() + inset - camera.z);
        float z1 = (float) (piece.pos.getZ() + 1.0D - inset - camera.z);
        int light = piece.packedLight;

        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.UP, sprites.up, piece, shades.up, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.DOWN, sprites.down, piece, shades.down, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.WEST, sprites.west, piece, shades.west, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.EAST, sprites.east, piece, shades.east, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.NORTH, sprites.north, piece, shades.north, light);
        emitFace(pose, consumer, x0, y0, z0, x1, y1, z1,
                Direction.SOUTH, sprites.south, piece, shades.south, light);
    }

    private static void emitFace(PoseStack.Pose pose, VertexConsumer consumer,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 Direction direction, FaceSprite face, DebrisPiece piece,
                                 int shade, int light) {

        int red = shade;
        int green = shade;
        int blue = shade;
        if (face.tintIndex >= 0 && face.tintIndex == piece.tintIndex && piece.tintColor != -1) {
            red = (((piece.tintColor >> 16) & 0xFF) * shade) / 255;
            green = (((piece.tintColor >> 8) & 0xFF) * shade) / 255;
            blue = ((piece.tintColor & 0xFF) * shade) / 255;
        }
        TextureAtlasSprite sprite = face.sprite;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        switch (direction) {
            case UP -> {

                vertex(pose, consumer, x0, y1, z1, red, green, blue, u0, v1, light, direction);
                vertex(pose, consumer, x1, y1, z1, red, green, blue, u1, v1, light, direction);
                vertex(pose, consumer, x1, y1, z0, red, green, blue, u1, v0, light, direction);
                vertex(pose, consumer, x0, y1, z0, red, green, blue, u0, v0, light, direction);
            }
            case DOWN -> {
                vertex(pose, consumer, x0, y0, z0, red, green, blue, u0, v1, light, direction);
                vertex(pose, consumer, x1, y0, z0, red, green, blue, u1, v1, light, direction);
                vertex(pose, consumer, x1, y0, z1, red, green, blue, u1, v0, light, direction);
                vertex(pose, consumer, x0, y0, z1, red, green, blue, u0, v0, light, direction);
            }
            case NORTH -> {
                vertex(pose, consumer, x1, y0, z0, red, green, blue, u0, v1, light, direction);
                vertex(pose, consumer, x0, y0, z0, red, green, blue, u1, v1, light, direction);
                vertex(pose, consumer, x0, y1, z0, red, green, blue, u1, v0, light, direction);
                vertex(pose, consumer, x1, y1, z0, red, green, blue, u0, v0, light, direction);
            }
            case SOUTH -> {
                vertex(pose, consumer, x0, y0, z1, red, green, blue, u0, v1, light, direction);
                vertex(pose, consumer, x1, y0, z1, red, green, blue, u1, v1, light, direction);
                vertex(pose, consumer, x1, y1, z1, red, green, blue, u1, v0, light, direction);
                vertex(pose, consumer, x0, y1, z1, red, green, blue, u0, v0, light, direction);
            }
            case WEST -> {
                vertex(pose, consumer, x0, y0, z0, red, green, blue, u0, v1, light, direction);
                vertex(pose, consumer, x0, y0, z1, red, green, blue, u1, v1, light, direction);
                vertex(pose, consumer, x0, y1, z1, red, green, blue, u1, v0, light, direction);
                vertex(pose, consumer, x0, y1, z0, red, green, blue, u0, v0, light, direction);
            }
            case EAST -> {
                vertex(pose, consumer, x1, y0, z1, red, green, blue, u0, v1, light, direction);
                vertex(pose, consumer, x1, y0, z0, red, green, blue, u1, v1, light, direction);
                vertex(pose, consumer, x1, y1, z0, red, green, blue, u1, v0, light, direction);
                vertex(pose, consumer, x1, y1, z1, red, green, blue, u0, v0, light, direction);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer,
                               float x, float y, float z, int red, int green, int blue,
                               float u, float v, int light, Direction direction) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setLight(light)
                .setNormal(pose, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static int shadeByte(float shade) {
        return Mth.clamp(Math.round(Mth.clamp(shade, 0.0F, 1.0F) * 255.0F), 0, 255);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unitFloat(long value) {
        return (float) ((value >>> 40) & 0xFFFFFFL) / 16777216.0F;
    }

    private static void clear() {
        ACTIVE_BURSTS.clear();
        activeDimension = null;
    }

    private record SurfaceSample(BlockPos pos, BlockState state, BlockState coverState) {}

    private record FaceSprite(TextureAtlasSprite sprite, int tintIndex) {}

    private record BlockSprites(FaceSprite up, FaceSprite down, FaceSprite north, FaceSprite south,
                                FaceSprite west, FaceSprite east) {}

    private record FaceShades(int up, int down, int north, int south, int west, int east) {}

    private record DebrisPiece(BlockPos pos, BlockState state, BlockState coverState, int packedLight, int tintIndex,
                               int tintColor, float startDelay) {}

    private static final class LandingBurst {
        private final long startGameTime;
        private final int maxLifetime;
        private final float pieceLifetime;
        private final List<List<DebrisPiece>> buckets;
        private final int tier;
        private final double originX;
        private final double landingY;
        private final double originZ;
        private final double half;
        private final double cosYaw;
        private final double sinYaw;
        private final double adjacentBlocks;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final long seedBase;
        private final boolean preferHeightmap;
        private final int centerX;
        private final int centerZ;
        private final int maxScanRadius;
        private final Map<BlockState, Integer> topTintIndexCache = new IdentityHashMap<>();

        private int scanRadius;
        private int ringIndex;
        private int nextX;
        private int nextZ;
        private boolean buildComplete;

        private LandingBurst(long startGameTime, int maxLifetime, float pieceLifetime,
                             List<List<DebrisPiece>> buckets, int tier,
                             double originX, double landingY, double originZ, double half,
                             double cosYaw, double sinYaw, double adjacentBlocks,
                             int minX, int maxX, int minZ, int maxZ,
                             long seedBase, boolean preferHeightmap) {
            this.startGameTime = startGameTime;
            this.maxLifetime = maxLifetime;
            this.pieceLifetime = pieceLifetime;
            this.buckets = buckets;
            this.tier = tier;
            this.originX = originX;
            this.landingY = landingY;
            this.originZ = originZ;
            this.half = half;
            this.cosYaw = cosYaw;
            this.sinYaw = sinYaw;
            this.adjacentBlocks = adjacentBlocks;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.seedBase = seedBase;
            this.preferHeightmap = preferHeightmap;
            this.centerX = Mth.floor(originX);
            this.centerZ = Mth.floor(originZ);
            this.maxScanRadius = Math.max(
                    Math.max(Math.abs(minX - this.centerX), Math.abs(maxX - this.centerX)),
                    Math.max(Math.abs(minZ - this.centerZ), Math.abs(maxZ - this.centerZ))
            );
        }

        private void buildAhead(ClientLevel level, int columnBudget) {
            if (this.buildComplete || columnBudget <= 0) return;

            Minecraft minecraft = Minecraft.getInstance();
            BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos lightProbe = new BlockPos.MutableBlockPos();

            int processed = 0;
            while (processed < columnBudget && nextScanColumn()) {
                ++processed;
                int x = this.nextX;
                int z = this.nextZ;
                if (x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ) {
                    continue;
                }

                double dx = x + 0.5D - this.originX;
                double dz = z + 0.5D - this.originZ;
                double localX = dx * this.cosYaw + dz * this.sinYaw;
                double localZ = -dx * this.sinYaw + dz * this.cosYaw;
                double outsideX = Math.max(0.0D, Math.abs(localX) - this.half);
                double outsideZ = Math.max(0.0D, Math.abs(localZ) - this.half);
                double outsideDistance = Math.sqrt(outsideX * outsideX + outsideZ * outsideZ);

                if (outsideX <= 0.0D && outsideZ <= 0.0D) continue;
                if (outsideDistance > this.adjacentBlocks) continue;

                long blockHash = mix64(this.seedBase ^ (((long) x) * 0xD6E8FEB86659FD93L)
                        ^ (((long) z) * 0xA5A3564E27F88621L));
                if (unitFloat(blockHash) >= 0.5F) {
                    continue;
                }

                SurfaceSample sample = findSurface(level, x, z, this.landingY, this.tier,
                        this.preferHeightmap, probe, above);
                if (sample == null) continue;

                float startDelay = Math.max(0.0F, (float) outsideDistance - 0.5F)
                        * OUTWARD_WAVE_TICKS_PER_BLOCK;
                lightProbe.set(sample.pos.getX(), sample.pos.getY() + 1, sample.pos.getZ());
                int packedLight = LevelRenderer.getLightColor(level, lightProbe);
                int tintIndex = this.topTintIndexCache.computeIfAbsent(sample.state,
                        state -> resolveTopTintIndex(minecraft, state));
                int tintColor = sampleTint(minecraft, level, sample, tintIndex);
                DebrisPiece piece = new DebrisPiece(sample.pos, sample.state, sample.coverState,
                        packedLight, tintIndex, tintColor, startDelay);
                int bucket = Mth.clamp(Mth.floor(startDelay), 0, this.buckets.size() - 1);
                this.buckets.get(bucket).add(piece);
            }
        }

        private boolean nextScanColumn() {
            if (this.buildComplete) return false;
            if (this.scanRadius > this.maxScanRadius) {
                this.buildComplete = true;
                return false;
            }

            if (this.scanRadius == 0) {
                this.nextX = this.centerX;
                this.nextZ = this.centerZ;
                this.scanRadius = 1;
                this.ringIndex = 0;
                return true;
            }

            int r = this.scanRadius;
            int side = r * 2;
            int segment = this.ringIndex / side;
            int offset = this.ringIndex % side;
            switch (segment) {
                case 0 -> { this.nextX = this.centerX - r + offset; this.nextZ = this.centerZ - r; }
                case 1 -> { this.nextX = this.centerX + r; this.nextZ = this.centerZ - r + offset; }
                case 2 -> { this.nextX = this.centerX + r - offset; this.nextZ = this.centerZ + r; }
                default -> { this.nextX = this.centerX - r; this.nextZ = this.centerZ + r - offset; }
            }

            ++this.ringIndex;
            if (this.ringIndex >= r * 8) {
                ++this.scanRadius;
                this.ringIndex = 0;
            }
            return true;
        }
    }
}
