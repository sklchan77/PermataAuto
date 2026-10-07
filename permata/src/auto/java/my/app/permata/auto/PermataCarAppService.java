package my.app.permata.auto;

import android.app.Presentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.IBinder;
import android.view.Gravity;
import android.view.Surface;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        
        // WebKit specific wrappers
        private VirtualDisplay virtualDisplay;
        private Presentation webKitPresentation;

        @NonNull
        @Override
        public Screen onCreateScreen(@NonNull Intent intent) {
            try {
                Intent bindIntent = new Intent();
                bindIntent.setClassName(getCarContext(), "my.app.permata.media.service.PermataMediaService");
                boolean bound = getCarContext().bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
                
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
                Method getCb = service.getClass().getMethod("getMediaSessionCallback");
                mediaSessionCallback = getCb.invoke(service);
                
                if (currentSurface != null) {
                    routeSurface();
                }
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to extract MediaSessionCallback", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaSessionCallback = null;
        }

        public void onSurfaceAvailable(Surface surface) {
            this.currentSurface = surface;
            if (mediaSessionCallback != null) {
                routeSurface();
            }
        }

        public void onSurfaceDestroyed() {
            destroyWebKitPresentation();
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

        private void routeSurface() {
            try {
                Method getEngine = mediaSessionCallback.getClass().getMethod("getEngine");
                MediaEngine engine = (MediaEngine) getEngine.invoke(mediaSessionCallback);
                
                if (engine != null) {
                    String engineName = engine.getClass().getSimpleName().toLowerCase();
                    if (engineName.contains("web") || engineName.contains("browser")) {
                        // Engine is WebKit: Must use a Presentation Window
                        mountWebKitPresentation(currentSurface);
                    } else {
                        // Engine is ExoPlayer/VLC: Use direct raw surface
                        attachSurfaceToEngine(currentSurface);
                    }
                }
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Error routing surface", e);
            }
        }

        private void mountWebKitPresentation(Surface surface) {
            try {
                // Use application context to satisfy Window Manager constraints
                Context appCtx = my.app.permata.PermataApplication.get();
                DisplayManager dm = (DisplayManager) appCtx.getSystemService(Context.DISPLAY_SERVICE);
                
                virtualDisplay = dm.createVirtualDisplay(
                        "PermataWebDisplay", 
                        1920, 1080, 160, 
                        surface, 
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                );

                webKitPresentation = new Presentation(appCtx, virtualDisplay.getDisplay());
                
                // --- MOUNT WEBKIT UI HERE ---
                // We create a visible fallback layout so you can confirm the window renders
                LinearLayout layout = new LinearLayout(appCtx);
                layout.setBackgroundColor(Color.parseColor("#121212"));
                layout.setGravity(Gravity.CENTER);
                
                TextView warning = new TextView(appCtx);
                warning.setText("WebKit Engine Detected.\nWebView requires this Presentation Window to render.\n\nTo view YouTube here, either:\n1. Switch Permata settings to ExoPlayer\n2. Mount your WebView instance into this Presentation layout.");
                warning.setTextColor(Color.WHITE);
                warning.setTextSize(24f);
                warning.setGravity(Gravity.CENTER);
                
                layout.addView(warning);
                webKitPresentation.setContentView(layout);
                // -----------------------------

                // Requires SYSTEM_ALERT_WINDOW permission (which your manifest already has)
                webKitPresentation.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                webKitPresentation.show();
                android.util.Log.i("PermataVideo", "WebKit Presentation mounted successfully.");

            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to mount WebKit Presentation", e);
            }
        }

        private void destroyWebKitPresentation() {
            if (webKitPresentation != null) {
                try {
                    webKitPresentation.dismiss();
                } catch (Exception ignored) {}
                webKitPresentation = null;
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
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
                }
            } catch (Throwable ignored) {}
        }
    }

    private static final class PermataVideoScreen extends Screen implements SurfaceCallback {
        private final PermataVideoSession session;

        PermataVideoScreen(@NonNull CarContext ctx, PermataVideoSession session) {
            super(ctx);
            this.session = session;
            try {
                ctx.getCarService(AppManager.class).setSurfaceCallback(this);
            } catch (Throwable ignored) {}
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
            if (sc.getSurface() != null) session.onSurfaceAvailable(sc.getSurface());
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
            session.onSurfaceDestroyed();
        }

        @Override
        public void onClick(float x, float y) {
            session.togglePlayPause();
        }

        @Override
        public void onScroll(float distanceX, float distanceY) {}

        @Override
        public void onScale(float focusX, float focusY, float scaleFactor) {}

        @NonNull
        @Override
        public Template onGetTemplate() {
            Action toggleAction = new Action.Builder()
                    .setTitle("Play / Pause")
                    .setOnClickListener(() -> session.togglePlayPause())
                    .build();

            return new NavigationTemplate.Builder()
                    .setActionStrip(new ActionStrip.Builder().addAction(toggleAction).build())
                    .build();
        }
    }
}