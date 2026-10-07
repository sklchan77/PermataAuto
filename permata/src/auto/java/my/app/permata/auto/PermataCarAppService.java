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

import java.lang.reflect.Method;

import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;

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
            ctx.getCarService(AppManager.class).setSurfaceCallback(this);
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
            Surface surface = sc.getSurface();
            if (surface == null) return;

            try {
                // Fix 1: Use the correct FutureSupplier retrieval method confirmed in VideoView.java
                FutureSupplier<MainActivityDelegate> future = MainActivityDelegate.getActivityDelegate(getCarContext());
                if (future == null) return;

                MainActivityDelegate delegate = future.peek();
                if (delegate != null) {
                    MediaSessionCallback cb = delegate.getMediaSessionCallback();
                    if (cb != null) {
                        MediaEngine engine = cb.getEngine();
                        if (engine != null) {
                            try {
                                Method m = engine.getClass().getMethod("setSurface", Surface.class);
                                m.invoke(engine, surface);
                                Log.i("[AUTO_FULLSCREEN] Attached Surface to engine.");
                            } catch (NoSuchMethodException e) {
                                try {
                                    Method m = engine.getClass().getMethod("setVideoSurface", Surface.class);
                                    m.invoke(engine, surface);
                                } catch (NoSuchMethodException ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                Log.e(e, "[AUTO_FULLSCREEN] Crash intercepted in onSurfaceAvailable");
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
            try {
                FutureSupplier<MainActivityDelegate> future = MainActivityDelegate.getActivityDelegate(getCarContext());
                if (future == null) return;

                MainActivityDelegate delegate = future.peek();
                if (delegate != null) {
                    MediaSessionCallback cb = delegate.getMediaSessionCallback();
                    if (cb != null) {
                        MediaEngine engine = cb.getEngine();
                        if (engine != null) {
                            try {
                                Method m = engine.getClass().getMethod("setSurface", Surface.class);
                                m.invoke(engine, new Object[]{null});
                            } catch (NoSuchMethodException e) {
                                try {
                                    Method m = engine.getClass().getMethod("setVideoSurface", Surface.class);
                                    m.invoke(engine, new Object[]{null});
                                } catch (NoSuchMethodException ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                Log.e(e, "[AUTO_FULLSCREEN] Crash intercepted in onSurfaceDestroyed");
            }
        }

        @Override
        public void onClick(float x, float y) {
            try {
                FutureSupplier<MainActivityDelegate> future = MainActivityDelegate.getActivityDelegate(getCarContext());
                if (future != null && future.peek() != null) {
                    MediaSessionCallback cb = future.peek().getMediaSessionCallback();
                    if (cb != null) {
                        if (cb.isPlaying()) cb.onPause();
                        else cb.onPlay();
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
            // Fix 2: Return a pure, empty NavigationTemplate to completely bypass the Action.PAN fatal crash
            return new NavigationTemplate.Builder().build();
        }
    }
}