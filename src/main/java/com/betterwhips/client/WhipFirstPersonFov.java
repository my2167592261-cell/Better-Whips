package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class WhipFirstPersonFov {
    private static final float VANILLA_HAND_FOV_DEGREES = 70.0F;
    private static final float EPSILON = 1.0E-4F;

    private WhipFirstPersonFov() {}

    static float projectionScale() {
        Minecraft minecraft = Minecraft.getInstance();
        float worldFov = minecraft.options == null
                ? VANILLA_HAND_FOV_DEGREES
                : minecraft.options.fov().get().floatValue();
        worldFov = Mth.clamp(worldFov, 1.0F, 179.0F);
        double handTan = Math.tan(Math.toRadians(VANILLA_HAND_FOV_DEGREES * 0.5D));
        double worldTan = Math.tan(Math.toRadians(worldFov * 0.5D));
        if (!Double.isFinite(worldTan) || Math.abs(worldTan) < 1.0E-8D) {
            return 1.0F;
        }
        return (float) (handTan / worldTan);
    }

    static void applyToViewModel(PoseStack stack) {
        float scale = projectionScale();
        if (Math.abs(scale - 1.0F) <= EPSILON) {
            return;
        }

        stack.scale(scale, scale, 1.0F);
    }

    static Vec3 worldVectorToCamera(Vec3 worldVector, Camera camera) {
        Quaternionf worldToCamera = new Quaternionf(camera.rotation()).conjugate();
        Vector3f view = worldVector.toVector3f();
        view.rotate(worldToCamera);
        return new Vec3(view.x, view.y, view.z);
    }

    static Vec3 cameraVectorToWorld(Vec3 cameraVector, Camera camera) {
        Quaternionf cameraToWorld = new Quaternionf(camera.rotation());
        Vector3f world = cameraVector.toVector3f();
        world.rotate(cameraToWorld);
        return new Vec3(world.x, world.y, world.z);
    }

    static Vec3 applyToCapturedWorldVector(Vec3 worldVector, Camera camera) {
        float scale = projectionScale();
        if (Math.abs(scale - 1.0F) <= EPSILON) {
            return worldVector;
        }
        Quaternionf cameraToWorld = new Quaternionf(camera.rotation());
        Quaternionf worldToCamera = new Quaternionf(cameraToWorld).conjugate();
        Vector3f view = worldVector.toVector3f();
        view.rotate(worldToCamera);
        view.x *= scale;
        view.y *= scale;
        view.rotate(cameraToWorld);
        return new Vec3(view.x, view.y, view.z);
    }

    static Vec3 removeFromCapturedWorldVector(Vec3 compensatedWorldVector, Camera camera) {
        float scale = projectionScale();
        if (Math.abs(scale - 1.0F) <= EPSILON || Math.abs(scale) < 1.0E-6F) {
            return compensatedWorldVector;
        }
        Quaternionf cameraToWorld = new Quaternionf(camera.rotation());
        Quaternionf worldToCamera = new Quaternionf(cameraToWorld).conjugate();
        Vector3f view = compensatedWorldVector.toVector3f();
        view.rotate(worldToCamera);
        view.x /= scale;
        view.y /= scale;
        view.rotate(cameraToWorld);
        return new Vec3(view.x, view.y, view.z);
    }
}
