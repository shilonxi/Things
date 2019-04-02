package com.example.administrator.things;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.auth.AuthService;
import com.netease.nimlib.sdk.auth.LoginInfo;
import com.netease.nimlib.sdk.msg.MessageBuilder;
import com.netease.nimlib.sdk.msg.MsgService;
import com.netease.nimlib.sdk.msg.constant.SessionTypeEnum;
import com.netease.nimlib.sdk.msg.model.IMMessage;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import pr.platerecognization.PlateRecognition;
/*
 */

public class Things_Activity extends Activity
{
    static {
        if(OpenCVLoader.initDebug())
            Log.d("Opencv","opencv load_success");
        else
            Log.d("Opencv","opencv can't load opencv");
    }
    private ImageView rImageView;
    private ImageView lImageView;
    private TextView rTextView;
    private TextView lTextView;
    //定义变量
    private static final String TAG="Things_Activity";
    private static final String RIGHT_PIN_NAME="BCM2";
    private static final String LEFT_PIN_NAME="BCM10";
    //定义端口
    private ButtonInputDriver right_driver;
    private ButtonInputDriver left_driver;
    //驱动事件
    private Capture capture1;
    private Bitmap bitmap1;
    //抓拍相关
    private long handle;
    //识别相关
    private String account="nanashi";
    //聊天对象的 ID
    private SessionTypeEnum sessionType=SessionTypeEnum.P2P;
    //单聊类型
    //转发相关

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Window w=getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        //硬加速
        setContentView(R.layout.things_layout);
        rImageView=findViewById(R.id.rightImage);
        lImageView=findViewById(R.id.leftImage);
        rTextView=findViewById(R.id.rightOutput);
        lTextView=findViewById(R.id.leftOutput);
        //获取实例
        capture1=new Capture();
        initRecognizer();
        //初始化
        doLogin();
        //手动登陆
        try
        {
            right_driver=new ButtonInputDriver(RIGHT_PIN_NAME,Button.LogicState.PRESSED_WHEN_LOW,KeyEvent.KEYCODE_SPACE);
            //低电平视为按钮被按下
            left_driver=new ButtonInputDriver(LEFT_PIN_NAME,Button.LogicState.PRESSED_WHEN_HIGH,KeyEvent.KEYCODE_1);
            //高电平视为按钮被按下
            //多输入，输入情况相异
            right_driver.register();
            left_driver.register();
            //注册
        } catch (IOException e) {
            Log.e(TAG,"error on driver",e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode,KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_SPACE)
        {
            capture1.openCamera(Things_Activity.this,0,getWindowManager().getDefaultDisplay().getRotation());
            //用CSI摄像头抓拍，多摄像头时为1
            bitmap1=capture1.getBitmap();
            rImageView.setImageBitmap(bitmap1);
            //显示抓拍到的图像
            String plate=null;
            if (bitmap1!=null)
                plate=SimpleRecog(bitmap1,3);
                //识别车牌号，分辨率较低
            if (plate!=null&&plate.length()!=0)
            {
                rTextView.setText(plate);
                //显示结果
                IMMessage textMessage=MessageBuilder.createTextMessage(account,sessionType,sysTime()+"|"+crossing()+"|"+plate);
                //数据要分三部分，时间，编号，车牌
                NIMClient.getService(MsgService.class).sendMessage(textMessage,false);
                //发送给对方
            }
            else
                rTextView.setText("无法识别");
            return true;
        }
        else if (keyCode==KeyEvent.KEYCODE_1)
        {
            //用USB摄像头抓拍，androidthings不支持
            return true;
        }
        return super.onKeyDown(keyCode,event);
    }
    //按钮按下响应，不能长按

    public void initRecognizer()
    {
        String assetPath="pr";
        String sdcardPath=Environment.getExternalStorageDirectory()+"/"+assetPath;
        copyFilesFromAssets(this,assetPath,sdcardPath);
        String cascade_filename=sdcardPath+"/"+"cascade.xml";
        String finemapping_prototxt=sdcardPath+"/"+"HorizonalFinemapping.prototxt";
        String finemapping_caffemodel=sdcardPath+"/"+"HorizonalFinemapping.caffemodel";
        String segmentation_prototxt=sdcardPath+"/"+"Segmentation.prototxt";
        String segmentation_caffemodel=sdcardPath+"/"+"Segmentation.caffemodel";
        String character_prototxt=sdcardPath+"/"+"CharacterRecognization.prototxt";
        String character_caffemodel=sdcardPath+"/"+"CharacterRecognization.caffemodel";
        //这些文件要存在
        handle= PlateRecognition.InitPlateRecognizer(
                cascade_filename,
                finemapping_prototxt,finemapping_caffemodel,
                segmentation_prototxt,segmentation_caffemodel,
                character_prototxt,character_caffemodel);
    }
    //初始化

    public void copyFilesFromAssets(Context context, String oldPath, String newPath)
    {
        try {
            String[] fileNames=context.getAssets().list(oldPath);
            if(fileNames.length>0)
            {
                File file=new File(newPath);
                if(!file.mkdir())
                {
                    Log.d("mkdir","can't make folder");
                }
                for(String fileName:fileNames)
                {
                    copyFilesFromAssets(context,oldPath+"/"+fileName,
                            newPath+"/"+fileName);
                }
            }
            else {
                InputStream is=context.getAssets().open(oldPath);
                FileOutputStream fos=new FileOutputStream(new File(newPath));
                byte[] buffer=new byte[1024];
                int byteCount;
                while((byteCount=is.read(buffer))!=-1)
                {
                    fos.write(buffer,0,byteCount);
                }
                fos.flush();
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //使用于initRecognizer()

    public String SimpleRecog(Bitmap bmp,int dp)
    {
        float dp_asp=dp/10.f;
        Mat mat_src=new Mat(bmp.getWidth(),bmp.getHeight(),CvType.CV_8UC4);
        float new_w=bmp.getWidth()*dp_asp;
        float new_h=bmp.getHeight()*dp_asp;
        Size sz=new Size(new_w,new_h);
        Utils.bitmapToMat(bmp,mat_src);
        Imgproc.resize(mat_src,mat_src,sz);
        String res=PlateRecognition.SimpleRecognization(mat_src.getNativeObjAddr(),handle);
        return res;
        //返回识别结果
    }
    //识别

    public void doLogin()
    {
        LoginInfo info=new LoginInfo("fxd","19950303");
        //登录信息不变
        RequestCallback<LoginInfo> callback=
                new RequestCallback<LoginInfo>() {
                    @Override
                    public void onSuccess(LoginInfo param) {
                        Toast.makeText(Things_Activity.this,"登录成功",Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailed(int code) {
                        Toast.makeText(Things_Activity.this,"登录失败",Toast.LENGTH_LONG).show();

                    }

                    @Override
                    public void onException(Throwable exception) {
                        Toast.makeText(Things_Activity.this,exception.toString(),Toast.LENGTH_LONG).show();
                    }
                };
        NIMClient.getService(AuthService.class).login(info).setCallback(callback);
        //发送请求
    }

    public String crossing()
    {
        int num=(int)(Math.random()*9+1);
        //产生1到9的随机数
        String crossing=""+num;
        return crossing;
    }
    //模拟各路口摄像头

    public String sysTime()
    {
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyyMMddHHmmss");
        //yyyy年MM月dd日 HH:mm:ss
        Date date=new Date(System.currentTimeMillis());
        String sysTime=simpleDateFormat.format(date);
        return sysTime;
    }
    //模拟车辆通过时间

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (right_driver!=null) {
            try {
                right_driver.unregister();
                //解注册
                right_driver.close();
            } catch (IOException e) {
                Log.e(TAG,"error on right",e);
            }
        }
        if (left_driver!=null) {
            try {
                left_driver.unregister();
                //解注册
                left_driver.close();
            } catch (IOException e) {
                Log.e(TAG,"error on left",e);
            }
        }
        //关闭资源
    }

}
