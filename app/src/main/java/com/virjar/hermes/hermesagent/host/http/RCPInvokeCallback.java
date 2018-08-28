package com.virjar.hermes.hermesagent.host.http;

import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.NameValuePair;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.body.JSONArrayBody;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.bean.CommonRes;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.host.thread.J2Executor;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;
import com.virjar.hermes.hermesagent.util.URLEncodeUtil;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import javax.annotation.Nullable;

/**
 * Created by virjar on 2018/8/24.
 */

public class RCPInvokeCallback implements HttpServerRequestCallback {
    private FontService fontService;
    private J2Executor j2Executor;

    RCPInvokeCallback(FontService fontService, J2Executor j2Executor) {
        this.fontService = fontService;
        this.j2Executor = j2Executor;
    }

    @Override
    public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
        final String invokePackage = determineInvokeTarget(request);
        if (StringUtils.isBlank(invokePackage)) {
            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_need_invoke_package_param, Constant.needInvokePackageParamMessage));
            return;
        }
        final IHookAgentService hookAgent = fontService.findHookAgent(invokePackage);
        if (hookAgent == null) {
            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_service_not_available, Constant.serviceNotAvailableMessage));
            return;
        }

        final InvokeRequest invokeRequest = buildInvokeRequest(request);
        if (invokeRequest == null) {
            CommonUtils.sendJSON(response, CommonRes.failed("unknown request data format"));
            return;
        }
        new J2ExecutorWrapper(j2Executor.getOrCreate(invokePackage, 2, 4),
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InvokeResult invokeResult = hookAgent.invoke(invokeRequest);
                            if (invokeResult == null) {
                                CommonUtils.sendJSON(response, CommonRes.failed("agent return null object"));
                                return;
                            }
                            if (invokeResult.getStatus() != InvokeResult.statusOK) {
                                CommonUtils.sendJSON(response, CommonRes.failed(invokeResult.getStatus(), invokeResult.getTheData()));
                                return;
                            }
                            if (invokeResult.getDataType() == InvokeResult.dataTypeJson) {
                                CommonUtils.sendJSON(response, CommonRes.success(com.alibaba.fastjson.JSON.parse(invokeResult.getTheData())));
                            } else {
                                CommonUtils.sendJSON(response, CommonRes.success(invokeResult.getTheData()));
                            }
                        } catch (DeadObjectException e) {
                            Log.e("rpcInvoke", "service " + invokePackage + " dead ,offline it", e);
                            fontService.releaseDeadAgent(invokePackage);
                            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_service_not_available, Constant.serviceNotAvailableMessage));
                        } catch (RemoteException e) {
                            CommonUtils.sendJSON(response, CommonRes.failed(e));
                        }
                    }
                }, response).run();

    }

    private String determineInvokeTarget(AsyncHttpServerRequest request) {
        Multimap query = request.getQuery();
        String invokePackage = query.getString(Constant.invokePackage);
        if (StringUtils.isBlank(invokePackage)) {
            Object o = request.getBody().get();
            if (o instanceof JSONObject) {
                invokePackage = ((JSONObject) o).optString(Constant.invokePackage);
            } else if (o instanceof String) {
                try {
                    com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSONObject.parseObject((String) o);
                    invokePackage = jsonObject.getString(Constant.invokePackage);
                } catch (com.alibaba.fastjson.JSONException e) {
                    //ignore
                }
            }
        }
        return invokePackage;
    }

    private Joiner joiner = Joiner.on('&').skipNulls();

    private String joinParam(Multimap params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return joiner.join(Iterables.transform(params, new Function<NameValuePair, String>() {
            @Override
            public String apply(@Nullable NameValuePair input) {
                if (input == null) {
                    return null;
                }
                return URLEncodeUtil.escape(input.getName()) + "=" + URLEncodeUtil.escape(input.getValue());
            }
        }));

    }

    private InvokeRequest buildInvokeRequest(AsyncHttpServerRequest request) {
        if ("get".equalsIgnoreCase(request.getMethod())) {
            return new InvokeRequest(joinParam(request.getQuery()), fontService);
        }

        AsyncHttpRequestBody requestBody = request.getBody();
        if (requestBody instanceof UrlEncodedFormBody) {
            return new InvokeRequest(joiner.join(joinParam(request.getQuery()),
                    joinParam(((UrlEncodedFormBody) requestBody).get())), fontService);
        }
        if (requestBody instanceof StringBody) {
            return new InvokeRequest(((StringBody) requestBody).get(), fontService);
        }
        if (requestBody instanceof JSONObjectBody) {
            JSONObjectBody jsonObjectBody = (JSONObjectBody) requestBody;
            JSONObject jsonObject = jsonObjectBody.get();
            return new InvokeRequest(jsonObject.toString(), fontService);
        }

        if (request instanceof JSONArrayBody) {
            return new InvokeRequest(((JSONArrayBody) request).get().toString(), fontService);
        }
        return null;
    }


}