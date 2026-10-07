package my.app.permata.auto;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
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

    private static class PermataVideoSession extends Session {
        @NonNull
        @Override
        public Screen onCreateScreen(@NonNull Intent intent) {
            return new PermataVideoScreen(getCarContext());
        }
    }

    private static final class PermataVideoScreen extends Screen implements SurfaceCallback {

        private VirtualDisplay virtualDisplay;
        private Surface currentSurface;

        PermataVideoScreen(@NonNull CarContext ctx) {
            super(ctx);
            try {
                ctx.getCarService(AppManager.class).setSurfaceCallback(this);
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to set surface callback", e);
            }
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
            currentSurface = sc.getSurface();
            if (currentSurface != null) {
                startVirtualDisplay();
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
            stopVirtualDisplay();
            currentSurface = null;
        }

        private void startVirtualDisplay() {
            if (currentSurface == null) return;
            
            try {
                DisplayManager displayManager = (DisplayManager) getCarContext().getSystemService(Context.DISPLAY_SERVICE);
                if (displayManager == null) return;

                // Standardized automotive wide-screen dimensions
                int width = 1920;
                int height = 1080;
                int densityDpi = DisplayMetrics.DENSITY_DEFAULT;

                // Create a VirtualDisplay that writes directly to the car's Surface.
                // This bypasses WebView restrictions by capturing the system drawing cache.
                virtualDisplay = displayManager.createVirtualDisplay(
                        "PermataAutoScreen",
                        width, height, densityDpi,
                        currentSurface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                );

                android.util.Log.i("PermataVideo", "VirtualDisplay mirroring started.");
            } catch (Throwable e) {
                android.util.Log.e("PermataVideo", "Failed to start VirtualDisplay", e);
            }
        }

        private void stopVirtualDisplay() {
            if (virtualDisplay != null) {
                try {
                    virtualDisplay.release();
                    virtualDisplay = null;
                    android.util.Log.i("PermataVideo", "VirtualDisplay mirroring stopped.");
                } catch (Throwable e) {
                    android.util.Log.e("PermataVideo", "Failed to release VirtualDisplay", e);
                }
            }
        }

        @Override
        public void onClick(float x, float y) {
            // Emulate a click event into the center of your application
            try {
                if (MainCarActivity.service != null) {
                    Object cb = MainCarActivity.service.getMediaSessionCallback();
                    if (cb != null) {
                        java.lang.reflect.Method isPlaying = cb.getClass().getMethod("isPlaying");
                        boolean playing = (boolean) isPlaying.invoke(cb);
                        if (playing) {
                            cb.getClass().getMethod("onPause").invoke(cb);
                        } else {
                            cb.getClass().getMethod("onPlay").invoke(cb);
                        }
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
                Action toggleAction = new Action.Builder()
                        .setTitle("Play / Pause")
                        .setOnClickListener(() -> onClick(0f, 0f))
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