package my.app.permata.auto;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
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

public class PermataCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
        return new PermataVideoSession();
    }

    private static class PermataVideoSession extends Session implements ServiceConnection {
        private Object mediaSessionCallback;
        private Surface currentSurface;
        private PermataVideoScreen screen;

        @NonNull
        @Override
        public Screen onCreateScreen(@NonNull Intent intent) {
            try {
                // Bind directly to the Media service so we do not rely on the phone UI being open.
                Intent bindIntent = new Intent();
                bindIntent.setClassName(getCarContext(), "my.app.permata.media.service.PermataMediaService");
                boolean bound = getCarContext().bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
                
                // Fallback via standard MediaBrowser intent if direct package lookup fails
                if (!bound) {
                    bindIntent.setPackage(getCarContext().getPackageName());
                    bindIntent.setAction("android.media.browse.MediaBrowserService");
                    getCarContext().bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
                }
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to bind to MediaService", e);
            }

            screen = new PermataVideoScreen(getCarContext(), this);
            return screen;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // The IBinder is your MediaServiceBinder. Extract the callback directly.
                Method getCb = service.getClass().getMethod("getMediaSessionCallback");
                mediaSessionCallback = getCb.invoke(service);
                android.util.Log.i("PermataVideo", "Successfully bound to MediaService and retrieved Callback");
                
                // If Android Auto already prepared the Surface before connection finished, attach it now
                if (currentSurface != null) {
                    attachSurfaceToEngine(currentSurface);
                }
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to extract MediaSessionCallback from Binder", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaSessionCallback = null;
        }

        public void onSurfaceAvailable(Surface surface) {
            this.currentSurface = surface;
            if (mediaSessionCallback != null) {
                attachSurfaceToEngine(surface);
            }
        }

        public void onSurfaceDestroyed() {
            if (mediaSessionCallback != null) {
                attachSurfaceToEngine(null);
            }
            this.currentSurface = null;
        }

        public void togglePlayPause() {
            try {
                if (mediaSessionCallback != null) {
                    Method isPlaying = mediaSessionCallback.getClass().getMethod("isPlaying");
                    boolean playing = (boolean) isPlaying.invoke(mediaSessionCallback);
                    if (playing) {
                        mediaSessionCallback.getClass().getMethod("onPause").invoke(mediaSessionCallback);
                    } else {
                        mediaSessionCallback.getClass().getMethod("onPlay").invoke(mediaSessionCallback);
                    }
                }
            } catch (Throwable ignored) {}
        }

        private void attachSurfaceToEngine(Surface surface) {
            try {
                Method getEngine = mediaSessionCallback.getClass().getMethod("getEngine");
                MediaEngine engine = (MediaEngine) getEngine.invoke(mediaSessionCallback);
                if (engine != null) {
                    try {
                        Method m = engine.getClass().getMethod("setSurface", Surface.class);
                        m.invoke(engine, surface);
                    } catch (Exception e1) {
                        try {
                            Method m = engine.getClass().getMethod("setVideoSurface", Surface.class);
                            m.invoke(engine, surface);
                        } catch (Exception e2) {}
                    }
                    android.util.Log.i("PermataVideo", surface != null ? "Surface Attached!" : "Surface Detached");
                }
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Error attaching surface to Engine", e);
            }
        }
    }

    private static final class PermataVideoScreen extends Screen implements SurfaceCallback {
        private final PermataVideoSession session;

        PermataVideoScreen(@NonNull CarContext ctx, PermataVideoSession session) {
            super(ctx);
            this.session = session;
            try {
                ctx.getCarService(AppManager.class).setSurfaceCallback(this);
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to set surface callback", e);
            }
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
            Surface surface = sc.getSurface();
            if (surface != null) {
                session.onSurfaceAvailable(surface);
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
            session.onSurfaceDestroyed();
        }

        @Override
        public void onClick(float x, float y) {
            // Allows tapping the video canvas itself to play/pause
            session.togglePlayPause();
        }

        @Override
        public void onScroll(float distanceX, float distanceY) {}

        @Override
        public void onScale(float focusX, float focusY, float scaleFactor) {}

        @NonNull
        @Override
        public Template onGetTemplate() {
            try {
                Action toggleAction = new Action.Builder()
                        .setTitle("Play / Pause")
                        .setOnClickListener(() -> session.togglePlayPause())
                        .build();

                ActionStrip actionStrip = new ActionStrip.Builder()
                        .addAction(toggleAction)
                        .build();

                return new NavigationTemplate.Builder()
                        .setActionStrip(actionStrip)
                        .build();
            } catch (Throwable e) {
                throw new IllegalStateException("Template generation failed", e);
            }
        }
    }
}