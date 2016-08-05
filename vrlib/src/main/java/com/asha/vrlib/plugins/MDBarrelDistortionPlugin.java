package com.asha.vrlib.plugins;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.asha.vrlib.MD360Director;
import com.asha.vrlib.MD360Program;
import com.asha.vrlib.MDVRLibrary;
import com.asha.vrlib.common.VRUtil;
import com.asha.vrlib.model.BarrelDistortionConfig;
import com.asha.vrlib.model.MDPosition;
import com.asha.vrlib.objects.MDAbsObject3D;
import com.asha.vrlib.objects.MDObject3DHelper;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static com.asha.vrlib.common.GLUtil.glCheck;

/**
 * Created by hzqiujiadi on 16/7/27.
 * hzqiujiadi ashqalcn@gmail.com
 *
 * Barrel Distortion
 *
 * For more info,
 * http://stackoverflow.com/questions/12620025/barrel-distortion-correction-algorithm-to-correct-fisheye-lens-failing-to-impl
 */
public class MDBarrelDistortionPlugin extends MDAbsPlugin {

    private MD360Program mProgram;

    private MDBarrelDistortionMesh object3D;

    private MD360Director mDirector;

    private int mFrameBufferId;

    private int mTextureId;

    private int mRenderBufferId;

    private Rect mViewport = new Rect();

    private BarrelDistortionConfig mConfiguration;

    public MDBarrelDistortionPlugin(BarrelDistortionConfig configuration) {
        mConfiguration = configuration;
        mProgram = new MD360Program(MDVRLibrary.ContentType.BITMAP);
        mDirector = new OrthogonalDirector(new MD360Director.Builder());
        object3D = new MDBarrelDistortionMesh();
    }

    @Override
    public void init(final Context context) {
        mProgram.build(context);
        MDObject3DHelper.loadObj(context,object3D);
    }

    public void createFrameBuffer(int width, int height){

        if (this.mTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[] { this.mTextureId }, 0);
        }
        if (this.mRenderBufferId != 0) {
            GLES20.glDeleteRenderbuffers(1, new int[] { this.mRenderBufferId }, 0);
        }
        if (this.mFrameBufferId != 0) {
            GLES20.glDeleteFramebuffers(1, new int[] { this.mFrameBufferId }, 0);
        }

        // frame buffer
        int[] frameBuffer = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0]);
        mFrameBufferId = frameBuffer[0];
        glCheck("MDBarrelDistortionPlugin frame buffer");

        // renderer buffer
        final int[] renderbufferIds = { 0 };
        GLES20.glGenRenderbuffers(1, renderbufferIds, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderbufferIds[0]);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);
        mRenderBufferId = renderbufferIds[0];
        glCheck("MDBarrelDistortionPlugin renderer buffer");

        final int[] textureIds = { 0 };
        GLES20.glGenTextures(1, textureIds, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, (Buffer)null);
        mTextureId = textureIds[0];
        glCheck("MDBarrelDistortionPlugin texture");

        // attach
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTextureId, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, renderbufferIds[0]);
        glCheck("MDBarrelDistortionPlugin attach");

        // check
        final int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            final String s = "Framebuffer is not complete: ";
            final String value = String.valueOf(Integer.toHexString(status));
            throw new RuntimeException((value.length() != 0) ? s.concat(value) : s);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        glCheck("MDBarrelDistortionPlugin attach");
    }

    @Override
    public void renderer(int index, int width, int height, MD360Director director) {

    }

    @Override
    public void destroy() {

    }

    @Override
    protected MDPosition getModelPosition() {
        return MDPosition.sOriginalPosition;
    }

    public void takeOver(int width, int height, int size) {
        mDirector.updateViewport(width, height);
        object3D.setMode(size);

        if (mViewport.width() != width || mViewport.height() != height){
            createFrameBuffer(width, height);
            mViewport.set(0,0,width,height);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, this.mFrameBufferId);
    }

    public void commit(int index){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        // Set our per-vertex lighting program.
        mProgram.use();
        glCheck("mProgram use");

        object3D.uploadVerticesBufferIfNeed(mProgram, index);
        object3D.uploadTexCoordinateBufferIfNeed(mProgram, index);

        // Pass in the combined matrix.
        mDirector.shot(mProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);

        object3D.draw();
    }

    private class OrthogonalDirector extends MD360Director{

        private OrthogonalDirector(Builder builder) {
            super(builder);
        }

        @Override
        public void setDeltaX(float mDeltaX) {
            // nop
        }

        @Override
        public void setDeltaY(float mDeltaY) {
            // nop
        }

        @Override
        public void updateSensorMatrix(float[] sensorMatrix) {
            // nop
        }

        @Override
        protected void updateProjection(){
            final float left = - 1f;
            final float right = 1f;
            final float bottom = - 1f;
            final float top = 1f;
            final float far = 500;
            Matrix.orthoM(getProjectionMatrix(), 0, left, right, bottom, top, getNear(), far);
        }
    }

    private class MDBarrelDistortionMesh extends MDAbsObject3D {

        private static final String TAG = "MDBarrelDistortionMesh";
        private int mode;
        private FloatBuffer singleTexCoordinateBuffer;

        public MDBarrelDistortionMesh() {
        }

        @Override
        public FloatBuffer getTexCoordinateBuffer(int index) {
            if (mode == 1){
                return singleTexCoordinateBuffer;
            } else if (mode == 2){
                return super.getTexCoordinateBuffer(index);
            } else {
                throw new RuntimeException("size of " + mode + " is not support in MDBarrelDistortionPlugin");
            }
        }

        @Override
        protected void executeLoad(Context context) {
            generateMesh(this);
        }

        private void generateMesh(MDAbsObject3D object3D){
            int rows = 10;
            int columns = 10;
            int numPoint = (rows + 1) * (columns + 1);
            short r, s;
            float z = -8;
            float R = 1f/(float) rows;
            float S = 1f/(float) columns;

            float[] vertexs = new float[numPoint * 3];
            float[] texcoords = new float[numPoint * 2];
            float[] texcoords1 = new float[numPoint * 2];
            float[] texcoords2 = new float[numPoint * 2];
            short[] indices = new short[numPoint * 6];


            int t = 0;
            int v = 0;
            for(r = 0; r < rows + 1; r++) {
                for(s = 0; s < columns + 1; s++) {
                    int tu = t++;
                    int tv = t++;

                    texcoords[tu] = s*S;
                    texcoords[tv] = r*R;

                    texcoords1[tu] = s*S*0.5f;
                    texcoords1[tv] = r*R;

                    texcoords2[tu] = s*S*0.5f + 0.5f;
                    texcoords2[tv] = r*R;

                    vertexs[v++] = (s * S * 2 - 1);
                    vertexs[v++] = (r * R * 2 - 1);
                    vertexs[v++] = z;
                }
            }

            applyBarrelDistortion(numPoint, vertexs);

            int counter = 0;
            int sectorsPlusOne = columns + 1;
            for(r = 0; r < rows; r++){
                for(s = 0; s < columns; s++) {
                    short k0 = (short) ((r) * sectorsPlusOne + (s+1));  // (c)
                    short k1 = (short) ((r+1) * sectorsPlusOne + (s));    //(b)
                    short k2 = (short) (r * sectorsPlusOne + s);       //(a);
                    short k3 = (short) ((r) * sectorsPlusOne + (s+1));  // (c)
                    short k4 = (short) ((r+1) * sectorsPlusOne + (s+1));  // (d)
                    short k5 = (short) ((r+1) * sectorsPlusOne + (s));    //(b)

                    indices[counter++] = k0;
                    indices[counter++] = k1;
                    indices[counter++] = k2;
                    indices[counter++] = k3;
                    indices[counter++] = k4;
                    indices[counter++] = k5;
                }
            }

            // initialize vertex byte buffer for shape coordinates
            ByteBuffer bb = ByteBuffer.allocateDirect(
                    // (# of coordinate values * 4 bytes per float)
                    vertexs.length * 4);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertexs);
            vertexBuffer.position(0);

            // initialize vertex byte buffer for shape coordinates
            ByteBuffer ee = ByteBuffer.allocateDirect(
                    texcoords.length * 4);
            ee.order(ByteOrder.nativeOrder());
            FloatBuffer texBuffer = ee.asFloatBuffer();
            texBuffer.put(texcoords);
            texBuffer.position(0);

            // initialize vertex byte buffer for shape coordinates
            ByteBuffer cc = ByteBuffer.allocateDirect(
                    texcoords1.length * 4);
            cc.order(ByteOrder.nativeOrder());
            FloatBuffer texBuffer1 = cc.asFloatBuffer();
            texBuffer1.put(texcoords1);
            texBuffer1.position(0);

            // initialize vertex byte buffer for shape coordinates
            ByteBuffer dd = ByteBuffer.allocateDirect(
                    texcoords2.length * 4);
            dd.order(ByteOrder.nativeOrder());
            FloatBuffer texBuffer2 = dd.asFloatBuffer();
            texBuffer2.put(texcoords2);
            texBuffer2.position(0);

            // initialize byte buffer for the draw list
            ByteBuffer dlb = ByteBuffer.allocateDirect(
                    // (# of coordinate values * 2 bytes per short)
                    indices.length * 2);
            dlb.order(ByteOrder.nativeOrder());
            ShortBuffer indexBuffer = dlb.asShortBuffer();
            indexBuffer.put(indices);
            indexBuffer.position(0);

            object3D.setIndicesBuffer(indexBuffer);
            object3D.setTexCoordinateBuffer(0,texBuffer1);
            object3D.setTexCoordinateBuffer(1,texBuffer2);
            object3D.setVerticesBuffer(0,vertexBuffer);
            object3D.setVerticesBuffer(1,vertexBuffer);
            object3D.setNumIndices(indices.length);

            singleTexCoordinateBuffer = texBuffer;
        }

        private void applyBarrelDistortion(int numPoint, float[] vertexs) {
            PointF pointF = new PointF();

            for (int i = 0; i < numPoint; i++){
                int xIndex = i * 3;
                int yIndex = i * 3 + 1;
                float xValue = vertexs[xIndex];
                float yValue = vertexs[yIndex];

                pointF.set(xValue,yValue);
                VRUtil.barrelDistortion(mConfiguration.getParamA(),
                        mConfiguration.getParamB(),
                        mConfiguration.getParamC(),
                        pointF);

                vertexs[xIndex] = pointF.x * mConfiguration.getScale();
                vertexs[yIndex] = pointF.y * mConfiguration.getScale();

                // Log.e(TAG,String.format("%f %f => %f %f",xValue,yValue,pointF.x,pointF.y));
            }
        }

        public void setMode(int mode) {
            this.mode = mode;
        }
    }

}
