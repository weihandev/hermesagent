package com.virjar.hermes.hermesagent.host.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.MainActivity;
import com.virjar.hermes.hermesagent.R;
import com.virjar.hermes.hermesagent.hermes_api.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IServiceRegister;
import com.virjar.hermes.hermesagent.host.http.HttpServer;
import com.virjar.hermes.hermesagent.host.manager.AgentWatchTask;
import com.virjar.hermes.hermesagent.host.manager.RefreshConfigTask;
import com.virjar.hermes.hermesagent.host.manager.ReportTask;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentMap;

import eu.chainfire.libsuperuser.Shell;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

/**
 * Created by virjar on 2018/8/22.<br>
 * 远程调用服务注册器，代码注入到远程apk之后，远程apk通过发现这个服务。注册自己的匿名binder，到这个容器里面来
 */

public class FontService extends Service {
    private static final String TAG = "AIDLRegisterService";
    private ConcurrentMap<String, IHookAgentService> allRemoteHookService = Maps.newConcurrentMap();
    public static RemoteCallbackList<IHookAgentService> mCallbacks = new RemoteCallbackList<>();
    public static Timer timer = null;
    private volatile long lastCheckTimerCheck = 0;
    private static final long aliveCheckDuration = 5000;
    private static final long timerCheckThreashHold = aliveCheckDuration * 4;
    private Set<String> onlineServices = null;

    //TODO 这个需要热发，和watch dog隔离
    private Set<String> allCallback = null;

    @SuppressWarnings("unchecked")
    private void scanCallBack() {
        allCallback = Sets.newHashSet("com.tencent.weishi");
        //无法使用类扫描机制，由于agent代码依赖xposed环境，宿主中，无法直接使用xposed代码
        //TODO 考虑xposed的mock
//        ClassScanner.SubClassVisitor<AgentCallback> subClassVisitor = new ClassScanner.SubClassVisitor(true, AgentCallback.class);
//        ClassScanner.scan(subClassVisitor, Sets.newHashSet(Constant.appHookSupperPackage));
//
//        allCallback = Sets.newHashSet(Iterables.filter(Lists.transform(subClassVisitor.getSubClass(), new Function<Class<? extends AgentCallback>, String>() {
//            @javax.annotation.Nullable
//            @Override
//            public String apply(@javax.annotation.Nullable Class<? extends AgentCallback> input) {
//                if (input == null) {
//                    return null;
//                }
//                try {
//                    return input.newInstance().targetPackageName();
//                } catch (InstantiationException | IllegalAccessException e) {
//                    Log.e("weijia", "failed to load create plugin", e);
//                }
//                return null;
//            }
//        }), new Predicate<String>() {
//            @Override
//            public boolean apply(@javax.annotation.Nullable String input) {
//                return StringUtils.isNotBlank(input);
//            }
//        }));

    }


    public void setOnlineServices(Set<String> onlineServices) {
        this.onlineServices = onlineServices;
    }


    private IServiceRegister.Stub binder = new IServiceRegister.Stub() {
        @Override
        public void registerHookAgent(IHookAgentService hookAgentService) throws RemoteException {
            if (hookAgentService == null) {
                throw new RemoteException("service register, service implement can not be null");
            }
            AgentInfo agentInfo = hookAgentService.ping();
            if (agentInfo == null) {
                Log.w(TAG, "service register,ping failed");
                return;
            }
            Log.i(TAG, "service " + agentInfo.getPackageName() + " register success");
            mCallbacks.register(hookAgentService);
            allRemoteHookService.putIfAbsent(agentInfo.getServiceAlis(), hookAgentService);
        }

        @Override
        public void unRegisterHookAgent(IHookAgentService hookAgentService) throws RemoteException {
            allRemoteHookService.remove(hookAgentService.ping().getServiceAlis());
            mCallbacks.unregister(hookAgentService);
        }

        @Override
        public List<String> onlineService() throws RemoteException {
            return Lists.newArrayList(onlineAgentServices());
        }
    };

    public IHookAgentService findHookAgent(String serviceName) {
        return allRemoteHookService.get(serviceName);
    }

    public void releaseDeadAgent(String serviceName) {
        allRemoteHookService.remove(serviceName);
    }

    public Set<String> onlineAgentServices() {
        if (onlineServices == null) {
            return allRemoteHookService.keySet();
        }
        return onlineServices;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        startService();
        return binder;
    }


    @Override
    public void onDestroy() {
        allRemoteHookService.clear();
        mCallbacks.kill();
        HttpServer.getInstance().stopServer();
        stopForeground(true);

        Intent intent = new Intent(Constant.fontServiceDestroyAction);
        sendBroadcast(intent);
        super.onDestroy();
    }

    private void startService() {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
        Intent nfIntent = new Intent(this, MainActivity.class);
        // 设置PendingIntent
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, FLAG_UPDATE_CURRENT))
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                .setContentTitle("HermasAgent") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("群控系统") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

        Notification notification = builder.build(); // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        startForeground(110, notification);// 开始前台服务

        if (!CommonUtils.xposedStartSuccess) {
            Log.w(TAG, "xposed 模块启动不正常，取消server启动");
            return;
        }

        if (allCallback == null) {
            scanCallBack();
        }

        //启动httpServer
        HttpServer.getInstance().setFontService(this);
        HttpServer.getInstance().startServer(this);

        if (lastCheckTimerCheck + timerCheckThreashHold < System.currentTimeMillis()) {
            if (lastCheckTimerCheck != 0) {
                Log.i(TAG, "timer 假死，重启timer");
            }
            restartTimer();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService();
        return START_STICKY;
    }

    private void restartTimer() {
        if (timer != null) {
            timer.cancel();
        }
        //之前的time可能死掉了
        timer = new Timer("FontServiceTimer", true);
        //监控所有agent状态
        timer.scheduleAtFixedRate(new AgentWatchTask(this, allRemoteHookService, allCallback, this), 1000, 2000);

        //半个小时，check一下adb的状态，守护adb进程
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    CommonUtils.enableADBTCPProtocol(FontService.this);
                } catch (Exception e) {
                    Log.e("weijia", "enable adb remote exception", e);
                }
            }
        }, 10, 1000 * 60 * 30);


        //平均每半个小时重启所有的targetApp
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (Map.Entry<String, IHookAgentService> entry : allRemoteHookService.entrySet()) {
                    try {
                        Log.i(TAG, "杀死targetApp");
                        entry.getValue().killSelf();
                    } catch (RemoteException e) {
                        //ignore
                    }
                }
            }
        }, 30 * 60 * 1000 + new Random().nextLong() % (30 * 60 * 1000), 60 * 60 * 1000);

        //半天，重启一次手机系统，避免系统跑死
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!Shell.SU.available()) {
                    //TODO test for jadb
                    return;
                }
                Shell.SU.run("reboot");
            }
        }, 6 * 60 * 60 * 1000 + new Random().nextLong() % (6 * 60 * 60 * 1000), 12 * 60 * 60 * 100);


        //注册存活检测，如果timer线程存活，那么lastCheckTimerCheck将会刷新，如果长时间不刷新，证明timer已经挂了
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                lastCheckTimerCheck = System.currentTimeMillis();
            }
        }, aliveCheckDuration, aliveCheckDuration);
        lastCheckTimerCheck = System.currentTimeMillis();

        if (!CommonUtils.isLocalTest()) {
            //向服务器上报服务信息,正式版本才进行上报，测试版本上报可能使得线上服务打到测试apk上面来
            timer.scheduleAtFixedRate(new ReportTask(this, this),
                    3000, 3000);
            //每隔2分钟拉取一次配置
            timer.scheduleAtFixedRate(new RefreshConfigTask(this), 120000, 120000);
        }

    }
}
