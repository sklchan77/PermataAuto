package my.app.permata.auto;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import my.app.utils.log.Log;

final class GlMirrorRenderer implements SurfaceTexture.OnFrameAvailableListener {
	private static final int EGL_RECORDABLE_ANDROID = 0x3142;

	private static final String VERTEX_SHADER =
			"attribute vec4 aPosition;\n" +
			"attribute vec4 aTexCoord;\n" +
			"uniform mat4 uTexMatrix;\n" +
			"uniform vec2 uScale;\n" +
			"varying vec2 vTexCoord;\n" +
			"void main() {\n" +
			"    gl_Position = vec4(aPosition.x * uScale.x, aPosition.y * uScale.y, 0.0, 1.0);\n" +
			"    vTexCoord = (uTexMatrix * aTexCoord).xy;\n" +
			"}\n";

	private static final String FRAGMENT_SHADER =
			"#extension GL_OES_EGL_image_external : require\n" +
			"precision mediump float;\n" +
			"varying vec2 vTexCoord;\n" +
			"uniform samplerExternalOES sTexture;\n" +
			"void main() {\n" +
			"    gl_FragColor = texture2D(sTexture, vTexCoord);\n" +
			"}\n";

	private static final float[] QUAD_POSITIONS = {
			-1f, -1f,
			 1f, -1f,
			-1f,  1f,
			 1f,  1f
	};

	private static final float[] QUAD_TEX_COORDS = {
			0f, 0f,
			1f, 0f,
			0f, 1f,
			1f, 1f
	};

	private final HandlerThread glThread;
	private final Handler glHandler;

	private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
	private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
	private EGLConfig eglConfig;
	private EGLSurface eglPbuffer = EGL14.EGL_NO_SURFACE;
	private EGLSurface eglWindowSurface = EGL14.EGL_NO_SURFACE;

	private int program = 0;
	private int oesTexId = 0;
	private int aPositionLoc;
	private int aTexCoordLoc;
	private int uTexMatrixLoc;
	private int uScaleLoc;

	private final FloatBuffer posBuffer;
	private final FloatBuffer texBuffer;
	private final float[] stMatrix = new float[16];

	private SurfaceTexture surfaceTexture;
	private Surface inputSurface;

	private int inputWidth;
	private int inputHeight;
	private int outputWidth;
	private int outputHeight;
	private boolean fillMode;
	private boolean hasFrame = false;
	private boolean loggedFirstFrame = false;
	private volatile boolean released = false;

	GlMirrorRenderer(int inputWidth, int inputHeight, boolean fillMode) {
		this.inputWidth = Math.max(2, inputWidth);
		this.inputHeight = Math.max(2, inputHeight);
		this.fillMode = fillMode;

		posBuffer = ByteBuffer.allocateDirect(QUAD_POSITIONS.length * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		posBuffer.put(QUAD_POSITIONS).position(0);

		texBuffer = ByteBuffer.allocateDirect(QUAD_TEX_COORDS.length * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		texBuffer.put(QUAD_TEX_COORDS).position(0);

		glThread = new HandlerThread("PermataGlMirror");
		glThread.start();
		glHandler = new Handler(glThread.getLooper());

		runSync(this::initGl);
		if (inputSurface == null) {
			release();
			throw new RuntimeException("Failed to initialize GlMirrorRenderer");
		}
	}

	Surface getInputSurface() {
		return inputSurface;
	}

	void setInputSize(int width, int height) {
		if (released) return;
		glHandler.post(() -> {
			if (released) return;
			inputWidth = Math.max(2, width);
			inputHeight = Math.max(2, height);
			if (surfaceTexture != null) {
				surfaceTexture.setDefaultBufferSize(inputWidth, inputHeight);
			}
			if (hasFrame) {
				renderFrame(false);
			}
		});
	}

	void setOutputSurface(@Nullable Surface surface, int width, int height) {
		if (released) return;
		glHandler.post(() -> {
			if (released) return;
			destroyWindowSurface(false);
			outputWidth = Math.max(1, width);
			outputHeight = Math.max(1, height);
			loggedFirstFrame = false;
			if (surface != null && surface.isValid() && eglDisplay != EGL14.EGL_NO_DISPLAY && eglConfig != null) {
				try {
					int[] surfaceAttribs = {EGL14.EGL_NONE};
					eglWindowSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0);
					if (eglWindowSurface == null || eglWindowSurface == EGL14.EGL_NO_SURFACE) {
						int err = EGL14.eglGetError();
						Log.e("[MIRROR][GL] eglCreateWindowSurface FAILED | size=" + width + "x" + height
								+ ", err=0x" + Integer.toHexString(err));
						eglWindowSurface = EGL14.EGL_NO_SURFACE;
					} else {
						Log.i("[MIRROR][GL] Output surface attached | size=" + width + "x" + height
								+ ", fillMode=" + fillMode);
						if (hasFrame) {
							renderFrame(false);
						}
					}
				} catch (Exception e) {
					Log.e(e, "[MIRROR][GL] Failed to attach output surface");
					eglWindowSurface = EGL14.EGL_NO_SURFACE;
				}
			} else {
				Log.w("[MIRROR][GL] setOutputSurface skipped | valid=" + (surface != null && surface.isValid()));
			}
		});
	}

	void detachOutputSurfaceSync() {
		if (released) return;
		runSync(() -> destroyWindowSurface(true));
	}

	void setFillMode(boolean fill) {
		if (released) return;
		glHandler.post(() -> {
			if (released) return;
			if (this.fillMode == fill) return;
			this.fillMode = fill;
			if (hasFrame) {
				renderFrame(false);
			}
		});
	}

	void release() {
		if (released) return;
		released = true;
		runSync(this::releaseGl);
		glThread.quitSafely();
	}

	@Override
	public void onFrameAvailable(SurfaceTexture st) {
		if (released) return;
		renderFrame(true);
	}

	private void initGl() {
		try {
			eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
			if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
				Log.e("[MIRROR][GL] eglGetDisplay failed");
				return;
			}

			int[] version = new int[2];
			if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
				Log.e("[MIRROR][GL] eglInitialize failed");
				return;
			}

			int[] recordableAttribs = {
					EGL14.EGL_RED_SIZE, 8,
					EGL14.EGL_GREEN_SIZE, 8,
					EGL14.EGL_BLUE_SIZE, 8,
					EGL14.EGL_ALPHA_SIZE, 8,
					EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
					EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
					EGL_RECORDABLE_ANDROID, 1,
					EGL14.EGL_NONE
			};
			EGLConfig[] configs = new EGLConfig[1];
			int[] numConfigs = new int[1];
			if (!EGL14.eglChooseConfig(eglDisplay, recordableAttribs, 0, configs, 0, 1, numConfigs, 0)
					|| numConfigs[0] <= 0) {
				int[] fallbackAttribs = {
						EGL14.EGL_RED_SIZE, 8,
						EGL14.EGL_GREEN_SIZE, 8,
						EGL14.EGL_BLUE_SIZE, 8,
						EGL14.EGL_ALPHA_SIZE, 8,
						EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
						EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
						EGL14.EGL_NONE
				};
				if (!EGL14.eglChooseConfig(eglDisplay, fallbackAttribs, 0, configs, 0, 1, numConfigs, 0)
						|| numConfigs[0] <= 0) {
					Log.e("[MIRROR][GL] eglChooseConfig failed");
					return;
				}
			}
			eglConfig = configs[0];

			int[] contextAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
			eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
			if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
				Log.e("[MIRROR][GL] eglCreateContext failed");
				return;
			}

			int[] pbufferAttribs = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
			eglPbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0);
			if (eglPbuffer == null || eglPbuffer == EGL14.EGL_NO_SURFACE) {
				Log.e("[MIRROR][GL] eglCreatePbufferSurface failed");
				return;
			}

			if (!EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)) {
				Log.e("[MIRROR][GL] eglMakeCurrent(pbuffer) failed");
				return;
			}

			program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER);
			if (program == 0) {
				Log.e("[MIRROR][GL] buildProgram failed");
				return;
			}

			aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition");
			aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord");
			uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix");
			uScaleLoc = GLES20.glGetUniformLocation(program, "uScale");

			int[] textures = new int[1];
			GLES20.glGenTextures(1, textures, 0);
			oesTexId = textures[0];
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

			surfaceTexture = new SurfaceTexture(oesTexId);
			surfaceTexture.setDefaultBufferSize(inputWidth, inputHeight);
			surfaceTexture.setOnFrameAvailableListener(this, glHandler);
			inputSurface = new Surface(surfaceTexture);
		} catch (Exception e) {
			Log.e(e, "[MIRROR][GL] initGl failed");
		}
	}

	private void renderFrame(boolean updateTex) {
		if (released || surfaceTexture == null) return;

		if (eglWindowSurface == EGL14.EGL_NO_SURFACE) {
			if (updateTex && eglPbuffer != EGL14.EGL_NO_SURFACE) {
				if (EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)) {
					try {
						surfaceTexture.updateTexImage();
						surfaceTexture.getTransformMatrix(stMatrix);
						hasFrame = true;
					} catch (Exception ignored) {}
				}
			}
			return;
		}

		if (!EGL14.eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext)) {
			int err = EGL14.eglGetError();
			if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_BAD_NATIVE_WINDOW) {
				Log.i("[MIRROR][GL] Output surface closed before frame | err=0x" + Integer.toHexString(err));
			} else {
				Log.e("[MIRROR][GL] eglMakeCurrent(window) FAILED | err=0x" + Integer.toHexString(err));
			}
			destroyWindowSurface(false);
			return;
		}

		if (updateTex) {
			try {
				surfaceTexture.updateTexImage();
				surfaceTexture.getTransformMatrix(stMatrix);
				hasFrame = true;
			} catch (Exception e) {
				return;
			}
		}

		float texAspect = (float) inputWidth / (float) inputHeight;
		float outAspect = (float) outputWidth / (float) outputHeight;
		float scaleX = 1f;
		float scaleY = 1f;

		if (fillMode) {
			if (texAspect > outAspect) {
				scaleX = texAspect / outAspect;
			} else {
				scaleY = outAspect / texAspect;
			}
		} else {
			if (texAspect > outAspect) {
				scaleY = outAspect / texAspect;
			} else {
				scaleX = texAspect / outAspect;
			}
		}

		GLES20.glViewport(0, 0, outputWidth, outputHeight);
		GLES20.glClearColor(0f, 0f, 0f, 1f);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

		GLES20.glUseProgram(program);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId);

		GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, stMatrix, 0);
		GLES20.glUniform2f(uScaleLoc, scaleX, scaleY);

		GLES20.glEnableVertexAttribArray(aPositionLoc);
		GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, posBuffer);

		GLES20.glEnableVertexAttribArray(aTexCoordLoc);
		GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

		GLES20.glDisableVertexAttribArray(aPositionLoc);
		GLES20.glDisableVertexAttribArray(aTexCoordLoc);

		if (!EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface)) {
			int err = EGL14.eglGetError();
			if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_BAD_NATIVE_WINDOW) {
				Log.i("[MIRROR][GL] Output surface closed mid-frame | err=0x" + Integer.toHexString(err));
				destroyWindowSurface(false);
			} else {
				Log.e("[MIRROR][GL] eglSwapBuffers FAILED | err=0x" + Integer.toHexString(err));
			}
		} else if (!loggedFirstFrame) {
			loggedFirstFrame = true;
			Log.i("[MIRROR][GL] First frame rendered | in=" + inputWidth + "x" + inputHeight
					+ ", out=" + outputWidth + "x" + outputHeight + ", fill=" + fillMode);
		}
	}

	private void destroyWindowSurface(boolean clearBlack) {
		if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglWindowSurface != EGL14.EGL_NO_SURFACE) {
			if (clearBlack && eglContext != EGL14.EGL_NO_CONTEXT) {
				try {
					if (EGL14.eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext)) {
						GLES20.glViewport(0, 0, Math.max(1, outputWidth), Math.max(1, outputHeight));
						GLES20.glClearColor(0f, 0f, 0f, 1f);
						GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
						EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface);
					}
				} catch (Exception ignored) {}
			}
			if (eglPbuffer != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
				EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext);
			} else {
				EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
			}
			EGL14.eglDestroySurface(eglDisplay, eglWindowSurface);
			eglWindowSurface = EGL14.EGL_NO_SURFACE;
		}
	}

	private void releaseGl() {
		destroyWindowSurface(true);
		if (inputSurface != null) {
			try { inputSurface.release(); } catch (Exception ignored) {}
			inputSurface = null;
		}
		if (surfaceTexture != null) {
			try {
				surfaceTexture.setOnFrameAvailableListener(null);
				surfaceTexture.release();
			} catch (Exception ignored) {}
			surfaceTexture = null;
		}
		if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
			if (eglPbuffer != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
				EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext);
			}
			if (oesTexId != 0) {
				GLES20.glDeleteTextures(1, new int[]{oesTexId}, 0);
				oesTexId = 0;
			}
			if (program != 0) {
				GLES20.glDeleteProgram(program);
				program = 0;
			}
			EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
			if (eglPbuffer != EGL14.EGL_NO_SURFACE) {
				EGL14.eglDestroySurface(eglDisplay, eglPbuffer);
				eglPbuffer = EGL14.EGL_NO_SURFACE;
			}
			if (eglContext != EGL14.EGL_NO_CONTEXT) {
				EGL14.eglDestroyContext(eglDisplay, eglContext);
				eglContext = EGL14.EGL_NO_CONTEXT;
			}
			EGL14.eglTerminate(eglDisplay);
			eglDisplay = EGL14.EGL_NO_DISPLAY;
		}
	}

	private void runSync(Runnable task) {
		if (Thread.currentThread() == glThread) {
			task.run();
			return;
		}
		CountDownLatch latch = new CountDownLatch(1);
		if (glHandler.post(() -> {
			try {
				task.run();
			} finally {
				latch.countDown();
			}
		})) {
			try {
				latch.await(500, TimeUnit.MILLISECONDS);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static int buildProgram(String vsSrc, String fsSrc) {
		int vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc);
		int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc);
		if (vs == 0 || fs == 0) return 0;
		int prog = GLES20.glCreateProgram();
		GLES20.glAttachShader(prog, vs);
		GLES20.glAttachShader(prog, fs);
		GLES20.glLinkProgram(prog);
		int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
		GLES20.glDeleteShader(vs);
		GLES20.glDeleteShader(fs);
		if (linkStatus[0] != GLES20.GL_TRUE) {
			GLES20.glDeleteProgram(prog);
			return 0;
		}
		return prog;
	}

	private static int compileShader(int type, String src) {
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, src);
		GLES20.glCompileShader(shader);
		int[] compiled = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0) {
			GLES20.glDeleteShader(shader);
			return 0;
		}
		return shader;
	}
}