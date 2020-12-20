/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.threema.app.video;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {
	private static final Logger logger = LoggerFactory.getLogger(OutputSurface.class);

	private static final int EGL_OPENGL_ES2_BIT = 4;
	private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
	private EGL10 mEGL;
	private EGLDisplay mEGLDisplay = null;
	private EGLContext mEGLContext = null;
	private EGLSurface mEGLSurface = null;
	private SurfaceTexture mSurfaceTexture;
	private Surface mSurface;
	private final Object mFrameSyncObject = new Object();
	private boolean mFrameAvailable;
	private TextureRenderer mTextureRender;
	private int mWidth;
	private int mHeight;
	private int rotateRender = 0;
	private ByteBuffer mPixelBuf;

	private HandlerThread mHandlerThread;
	private Handler mHandler;

	public OutputSurface(int width, int height, int rotate) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException();
		}
		mWidth = width;
		mHeight = height;
		rotateRender = rotate;
		mPixelBuf = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
		mPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
		eglSetup(width, height);
		makeCurrent();
		setup();
	}

	public OutputSurface() {
		setup();
	}

	private void setup() {
		mTextureRender = new TextureRenderer(rotateRender);
		mTextureRender.surfaceCreated();

		// https://stackoverflow.com/a/55968224/284318
		mHandlerThread = new HandlerThread("OutputSurfaceCallback");
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper());

		// Even if we don't access the SurfaceTexture after the constructor returns, we
		// still need to keep a reference to it.  The Surface doesn't retain a reference
		// at the Java level, so if we don't either then the object can get GCed, which
		// causes the native finalizer to run.
		logger.debug("textureID=" + mTextureRender.getTextureId());
		mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mSurfaceTexture.setOnFrameAvailableListener(this, mHandler);
		} else {
			mSurfaceTexture.setOnFrameAvailableListener(this);
		}
		mSurface = new Surface(mSurfaceTexture);
	}

	private void eglSetup(int width, int height) {
		mEGL = (EGL10) EGLContext.getEGL();
		mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

		if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
			throw new RuntimeException("unable to get EGL10 display");
		}

		if (!mEGL.eglInitialize(mEGLDisplay, null)) {
			mEGLDisplay = null;
			throw new RuntimeException("unable to initialize EGL10");
		}

		int[] attribList = {
			EGL10.EGL_RED_SIZE, 8,
			EGL10.EGL_GREEN_SIZE, 8,
			EGL10.EGL_BLUE_SIZE, 8,
			EGL10.EGL_ALPHA_SIZE, 8,
			EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
			EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
			EGL10.EGL_NONE
		};
		EGLConfig[] configs = new EGLConfig[1];
		int[] numConfigs = new int[1];
		if (!mEGL.eglChooseConfig(mEGLDisplay, attribList, configs, configs.length, numConfigs)) {
			throw new RuntimeException("unable to find RGB888+pbuffer EGL config");
		}
		int[] attrib_list = {
			EGL_CONTEXT_CLIENT_VERSION, 2,
			EGL10.EGL_NONE
		};
		mEGLContext = mEGL.eglCreateContext(mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT, attrib_list);
		checkEglError("eglCreateContext");
		if (mEGLContext == null) {
			throw new RuntimeException("null context");
		}
		int[] surfaceAttribs = {
			EGL10.EGL_WIDTH, width,
			EGL10.EGL_HEIGHT, height,
			EGL10.EGL_NONE
		};
		mEGLSurface = mEGL.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs);
		checkEglError("eglCreatePbufferSurface");
		if (mEGLSurface == null) {
			throw new RuntimeException("surface was null");
		}
	}

	public void release() {
		if (mEGL != null) {
			if (mEGL.eglGetCurrentContext().equals(mEGLContext)) {
				mEGL.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
			}
			mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface);
			mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);
		}
		mSurface.release();
		mEGLDisplay = null;
		mEGLContext = null;
		mEGLSurface = null;
		mEGL = null;
		mTextureRender = null;
		mSurface = null;
		mSurfaceTexture = null;
	}

	public void makeCurrent() {
		if (mEGL == null) {
			throw new RuntimeException("not configured for makeCurrent");
		}
		checkEglError("before makeCurrent");
		if (!mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
			throw new RuntimeException("eglMakeCurrent failed");
		}
	}

	public Surface getSurface() {
		return mSurface;
	}

	public void awaitNewImage() {
		final int TIMEOUT_MS = 2500;
		synchronized (mFrameSyncObject) {
			while (!mFrameAvailable) {
				try {
					mFrameSyncObject.wait(TIMEOUT_MS);
					if (!mFrameAvailable) {
						throw new RuntimeException("Surface frame wait timed out");
					}
				} catch (InterruptedException ie) {
					throw new RuntimeException(ie);
				}
			}
			mFrameAvailable = false;
		}
		mTextureRender.checkGlError("before updateTexImage");
		mSurfaceTexture.updateTexImage();
	}

	public void drawImage(boolean invert) {
		mTextureRender.drawFrame(mSurfaceTexture, invert);
	}

	@Override
	public void onFrameAvailable(SurfaceTexture st) {
		synchronized (mFrameSyncObject) {
			if (mFrameAvailable) {
				throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
			}
			mFrameAvailable = true;
			mFrameSyncObject.notifyAll();
		}
	}

	public ByteBuffer getFrame() {
		mPixelBuf.rewind();
		GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixelBuf);
		return mPixelBuf;
	}

	private void checkEglError(String msg) {
		if (mEGL.eglGetError() != EGL10.EGL_SUCCESS) {
			throw new RuntimeException("EGL error encountered (see log)");
		}
	}
}
