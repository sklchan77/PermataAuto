package my.app.permata.auto;

import android.content.Intent;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import my.app.permata.media.engine.MediaEngine;

public class PermataCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new PermataVideoScreen(getCarContext());
            }
        };
    }

    private static final class PermataVideoScreen extends Screen implements SurfaceCallback {

        PermataVideoScreen(@NonNull CarContext ctx) {
            super(ctx);
            try {
                // Attach surface safely so if the API level is mismatched, it doesn't crash the car
                ctx.getCarService(AppManager.class).setSurfaceCallback(this);
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to set surface callback", e);
            }
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
            try {
                Surface surface = sc.getSurface();
                if (surface == null) return;

                MediaEngine engine = findActiveEngineSafely();
                if (engine != null) {
                    try {
                        Method m = engine.getClass().getMethod("setSurface", Surface.class);
                        m.invoke(engine, surface);
                        android.util.Log.i("PermataVideo", "Attached native Surface to engine");
                    } catch (Exception e1) {
                        try {
                            Method m = engine.getClass().getMethod("setVideoSurface", Surface.class);
                            m.invoke(engine, surface);
                            android.util.Log.i("PermataVideo", "Attached native VideoSurface to engine");
                        } catch (Exception e2) {
                            android.util.Log.e("PermataVideo", "Engine found, but no surface method supported.");
                        }
                    }
                } else {
                    android.util.Log.w("PermataVideo", "No active engine found to attach surface to.");
                }
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Crash trapped in onSurfaceAvailable", e);
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
            try {
                MediaEngine engine = findActiveEngineSafely();
                if (engine != null) {
                    try {
                        Method m = engine.getClass().getMethod("setSurface", Surface.class);
                        m.invoke(engine, new Object[]{null});
                    } catch (Exception e1) {
                        try {
                            Method m = engine.getClass().getMethod("setVideoSurface", Surface.class);
                            m.invoke(engine, new Object[]{null});
                        } catch (Exception e2) {}
                    }
                }
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Crash trapped in onSurfaceDestroyed", e);
            }
        }

        @Override
        public void onClick(float x, float y) {
            try {
                Object cb = findMediaSessionCallbackSafely();
                if (cb != null) {
                    Method isPlaying = cb.getClass().getMethod("isPlaying");
                    boolean playing = (boolean) isPlaying.invoke(cb);
                    if (playing) {
                        cb.getClass().getMethod("onPause").invoke(cb);
                    } else {
                        cb.getClass().getMethod("onPlay").invoke(cb);
                    }
                }
            } catch (Throwable ignored) {}
        }

        @Override
        public void onScroll(float distanceX, float distanceY) {}

        @Override
        public void onScale(float focusX, float focusY, float scaleFactor) {}

        @NonNull
        @Override
        public Template onGetTemplate() {
            try {
                // A completely barebones navigation template. 
                // We do not add Action.PAN or MapActionStrip here because failing to provide 
                // a strict PanModeDelegate or satisfying map requirements causes instant car crashes.
                return new NavigationTemplate.Builder().build();
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Template generation failed", e);
                throw e; // Car libraries require a template to be returned, so if it fails, let it fallback.
            }
        }

        // -------------------------------------------------------------------------
        // PURE REFLECTION HELPERS
        // Using reflection bypasses all ClassDefNotFound/VerifyError crashes 
        // that occur when Android Auto runs in the background.
        // -------------------------------------------------------------------------

        private Object findMediaSessionCallbackSafely() {
            try {
                // Route 1: Try via MainActivityDelegate without importing the class
                Object app = my.app.permata.PermataApplication.get();
                Class<?> delegateClass = Class.forName("my.app.permata.ui.activity.MainActivityDelegate");
                Method getDelegate = delegateClass.getMethod("getActivityDelegate", android.content.Context.class);
                Object future = getDelegate.invoke(null, app);
                
                if (future != null) {
                    Method peek = future.getClass().getMethod("peek");
                    Object delegate = peek.invoke(future);
                    if (delegate != null) {
                        try {
                            Method getCb = delegate.getClass().getMethod("getMediaSessionCallback");
                            return getCb.invoke(delegate);
                        } catch (Exception e) {
                            Method getBinder = delegate.getClass().getMethod("getMediaServiceBinder");
                            Object binder = getBinder.invoke(delegate);
                            if (binder != null) {
                                Method getCb = binder.getClass().getMethod("getMediaSessionCallback");
                                return getCb.invoke(binder);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                // Route 2: Fallback to your legacy MainCarActivity static reference if it exists
                Class<?> carActivityClass = Class.forName("my.app.permata.auto.MainCarActivity");
                Field serviceField = carActivityClass.getField("service");
                Object service = serviceField.get(null);
                if (service != null) {
                    Method getCb = service.getClass().getMethod("getMediaSessionCallback");
                    return getCb.invoke(service);
                }
            } catch (Throwable ignored) {}

            return null;
        }

        private MediaEngine findActiveEngineSafely() {
            try {
                Object cb = findMediaSessionCallbackSafely();
                if (cb != null) {
                    Method getEngine = cb.getClass().getMethod("getEngine");
                    return (MediaEngine) getEngine.invoke(cb);
                }
            } catch (Throwable ignored) {}
            return null;
        }
    }
}