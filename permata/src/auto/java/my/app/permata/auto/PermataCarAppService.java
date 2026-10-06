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
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;

import java.lang.reflect.Method;

import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.utils.log.Log;

public class PermataCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Fix 1: Required by Android Auto API 5+
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
                // Fix 2: Route through MainCarActivity exactly like your CarService.java does
                PermataMediaServiceConnection s = MainCarActivity.service;
                if (s != null) {
                    MediaSessionCallback cb = s.getMediaSessionCallback();
                    if (cb != null) {
                        MediaEngine engine = cb.getEngine();
                        if (engine != null) {
                            // Fix 3: Use reflection to bypass MediaEngine interface limitations 
                            // without needing to modify your ExoPlayer/VLC engine files directly.
                            try {
                                Method m = engine.getClass().getMethod("setSurface", Surface.class);
                                m.invoke(engine, surface);
                                Log.i("[AUTO_FULLSCREEN] Attached Surface via setSurface().");
                            } catch (NoSuchMethodException e) {
                                try {
                                    Method m = engine.getClass().getMethod("setVideoSurface", Surface.class);
                                    m.invoke(engine, surface);
                                    Log.i("[AUTO_FULLSCREEN] Attached Surface via setVideoSurface().");
                                } catch (NoSuchMethodException e2) {
                                    Log.e("[AUTO_FULLSCREEN] MediaEngine missing Surface injection method.");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(e, "[AUTO_FULLSCREEN] Error attaching Surface to MediaEngine");
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
            try {
                PermataMediaServiceConnection s = MainCarActivity.service;
                if (s != null) {
                    MediaSessionCallback cb = s.getMediaSessionCallback();
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
                                } catch (NoSuchMethodException e2) {}
                            }
                            Log.i("[AUTO_FULLSCREEN] Detached Android Auto Surface from MediaEngine.");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(e, "[AUTO_FULLSCREEN] Error detaching Surface");
            }
        }

        @Override
        public void onClick(float x, float y) {
            try {
                PermataMediaServiceConnection s = MainCarActivity.service;
                if (s != null) {
                    MediaSessionCallback cb = s.getMediaSessionCallback();
                    if (cb != null) {
                        if (cb.isPlaying()) {
                            cb.onPause();
                        } else {
                            cb.onPlay();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            return new NavigationTemplate.Builder()
                    .setMapActionStrip(new ActionStrip.Builder().addAction(Action.PAN).build())
                    .build();
        }
    }
}