package com.example.administrator.things;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Capture
{
    private static final SparseIntArray ORIENTATIONS=new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    //方向旋转
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    //摄像相关
    private HandlerThread handlerThread;
    private Handler childHandler;
    //线程与子线程
    private ImageReader mImageReader;
    private Bitmap bitmap;
    //处理相片
    private int rotation;
    private Context context;

    public Capture() {

    }

    public void openCamera(Context context,int number,int rotation) {
        this.rotation=rotation;
        this.context=context;
        //传参
        mCameraManager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        //获取服务
        String cameraIds[]={};
        try {
            cameraIds=mCameraManager.getCameraIdList();
            //获取摄像头列表
        } catch (CameraAccessException e) {
            Log.e("camera", "ID exception", e);
        }
        String mCameraID=cameraIds[number];
        //锁定对应摄像头
        try {
            mCameraManager.openCamera(mCameraID, stateCallback, null);
            //打开摄像头
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback stateCallback=new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice=camera;
            //打开摄像头
            takePicture(rotation);
            //拍照
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (null!=mCameraDevice)
                mCameraDevice.close();
            mCameraDevice=null;
            //关闭摄像头
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraDevice=null;
        }
    };
    //回调监听

    private void takePicture(int rotation)
    {
        handlerThread=new HandlerThread("Camera2");
        handlerThread.start();
        childHandler=new Handler(handlerThread.getLooper());
        //异步处理
        mImageReader=ImageReader.newInstance(320, 240, ImageFormat.JPEG, 1);
        //相片设置
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener()
        {
            @Override
            public void onImageAvailable(ImageReader reader)
            {
                mCameraDevice.close();
                mCameraDevice=null;
                //关闭摄像头
                Image image=reader.acquireLatestImage();
                //获取预览图像
                ByteBuffer buffer=image.getPlanes()[0].getBuffer();
                byte[] bytes=new byte[buffer.remaining()];
                buffer.get(bytes);
                //由缓冲区存入字节数组
                bitmap= BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                bitmap=Bitmap.createScaledBitmap(bitmap, 320, 240, true);
                //生成图片待显示
            }
        }, childHandler);
        try
        {
            final CaptureRequest.Builder builder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //创建适用于静态图像捕获的请求
            builder.addTarget(mImageReader.getSurface());
            builder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));
            //根据设备方向计算设置照片的方向
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (mCameraDevice==null)
                        return;
                    try {
                        CaptureRequest mCaptureRequest=builder.build();
                        session.capture(mCaptureRequest, null, childHandler);
                        //捕获静态图像
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            },childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
}
