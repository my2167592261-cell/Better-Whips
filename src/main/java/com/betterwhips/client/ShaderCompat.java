package com.betterwhips.client;

import java.lang.reflect.Method;

public final class ShaderCompat {
    private static final Object IRIS_API;
    private static final Method IRIS_SHADER_IN_USE;
    private static final Method IRIS_SHADOW_PASS;

    static {
        Object api = null;
        Method shaderInUse = null;
        Method shadowPass = null;
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstance = irisApiClass.getMethod("getInstance");
            api = getInstance.invoke(null);
            shaderInUse = irisApiClass.getMethod("isShaderPackInUse");
            shadowPass = irisApiClass.getMethod("isRenderingShadowPass");
        } catch (Throwable ignored) {

        }
        IRIS_API = api;
        IRIS_SHADER_IN_USE = shaderInUse;
        IRIS_SHADOW_PASS = shadowPass;
    }

    private ShaderCompat() {}

    public static boolean isIrisShaderPackInUse() {
        return invokeBoolean(IRIS_SHADER_IN_USE);
    }

    public static boolean isIrisShadowPass() {
        return invokeBoolean(IRIS_SHADOW_PASS);
    }

    private static boolean invokeBoolean(Method method) {
        if (IRIS_API == null || method == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(IRIS_API));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
