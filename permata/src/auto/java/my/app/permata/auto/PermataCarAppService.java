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

import my.app.permata.PermataApplication;
import my.app.permata.media.engine.MediaEngine;
import my.app.utils.log.Log;

public class PermataCarAppService extends CarAppService {

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
                var app = PermataApplication.get();
                if (app != null && app.getMediaServiceConnection() != null) {
                    var cb = app.getMediaServiceConnection().getMediaSessionCallback();
                    if (cb != null) {
                        MediaEngine engine = cb.getEngine();
                        if (engine != null) {
                            engine.setSurface(surface);
                            Log.i("[AUTO_FULLSCREEN] Attached Android Auto Surface directly to MediaEngine.");
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
                var app = PermataApplication.get();
                if (app != null && app.getMediaServiceConnection() != null) {
                    var cb = app.getMediaServiceConnection().getMediaSessionCallback();
                    if (cb != null && cb.getEngine() != null) {
                        cb.getEngine().setSurface(null);
                        Log.i("[AUTO_FULLSCREEN] Detached Android Auto Surface from MediaEngine.");
                    }
                }
            } catch (Exception e) {
                Log.e(e, "[AUTO_FULLSCREEN] Error detaching Surface");
            }
        }

        @Override
        public void onClick(float x, float y) {
            try {
                var app = PermataApplication.get();
                if (app != null && app.getMediaServiceConnection() != null) {
                    var cb = app.getMediaServiceConnection().getMediaSessionCallback();
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