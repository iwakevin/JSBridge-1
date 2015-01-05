package com.siva4u.jsbridge;

import java.io.IOException;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

@SuppressLint({ "NewApi", "SetJavaScriptEnabled", "JavascriptInterface" })
public class JSBridge {
	
	private Context				context			= null;
	private WebView				webView			= null;
	private JSBridgeInteface	jsListener		= null;
	private JSBridgeHandler		jsBridgeHandler	= null;
	
	private int			uniqueId			= 0;
	private JSONArray	startupMessageQueue	= new JSONArray();
	private JSONObject	responseCallbacks	= new JSONObject();
	private JSONObject	messageHandlers		= new JSONObject();

	public static final String JS_BRIDGE_FILE_NAME			= "JSBridge.min.js";
	public static final String JS_BRIDGE					= "JSBridge";
	public static final String JS_BRIDGE_SEND_NATIVE_QUEUE	= "_handleMessageFromNative";

	public static void Log(String str) {
		System.out.println("JSBridge: Log: "+str);
	}
	public static String getString(JSONObject obj, String forKey) {
   		try {
   			if(forKey != null) return obj.get(forKey).toString();
   			else {
   				return obj.toString();
   			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
    	return null;
    }
	public static String getString(JSONObject obj) {
    	return getString(obj, null);
    }
	public static JSONObject getJSONObject(String str) {
    	try {
			return new JSONObject(str);
		} catch (JSONException e) {
			e.printStackTrace();
		}
    	try {
			return new JSONObject("{'data':"+str+"}");
		} catch (JSONException e) {
			e.printStackTrace();
		}
    	return new JSONObject();
    }
	public static JSONObject updateJsonObject(JSONObject obj, String key, String value) {
		try {
			if((obj != null) && (key != null) && (value != null)) {
				return obj.put(key,value);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return obj;
	}
	public static JSONObject updateJsonObject(JSONObject obj, String key, JSONObject value) {
		try {
			if((obj != null) && (key != null) && (value != null)) {
				return obj.put(key,value);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return obj;
	}
	public static JSONObject updateJsonObject(JSONObject obj, String key, Object value) {
		try {
			if((obj != null) && (key != null) && (value != null)) {
				return obj.put(key,value);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return obj;
	}
	
	private final String getAssetFileContents(String fileName) {
	      String contents = "";
	      try {
	          InputStream stream = context.getAssets().open(fileName);
	          byte[] buffer = new byte[stream.available()];
	          stream.read(buffer);
	          stream.close();
	          contents = new String(buffer);
	      } catch (IOException e) {}
	      return contents;
	}
	
	private class JSBridgeWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if(jsListener != null) {
				jsListener.shouldOverrideUrlLoading(view, url);
			}
			return false;
		}
		@Override
		public void onPageStarted (WebView view, String url, Bitmap favicon) {
			if(jsListener != null) {
				jsListener.onPageStarted(view, url, favicon);
			}
		}
		@Override
		public void onPageFinished(WebView view, String url) {			
			if(startupMessageQueue != null) {
				int queueCount = startupMessageQueue.length();
				if(queueCount > 0) {
					for (int i = 0; i < queueCount; i++) {
						try {
							dispatchMessage(startupMessageQueue.getString(i));
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				}				
		        startupMessageQueue = null;
			}
			if(jsListener != null) {
				jsListener.onPageFinished(view,url);
			}
		}
	}

	private class AndroidAPI extends JSBridgeAPIBase {
		public AndroidAPI(Context c, WebView view) {
			super(c, view);
		}

		@JavascriptInterface
		public void NativeAPI(String msgQueue) {
			try {
				JSONArray messages = new JSONArray(msgQueue);
				if(messages != null) {
					int msgCount = messages.length();
					if(msgCount > 0) {
						for (int i = 0; i < msgCount; i++) {
							JSONObject message = messages.getJSONObject(i);
							if(message != null) {
								JSBridgeCallback responseCallback = null;
								String responseId = getString(message,"responseId");
								if(responseId != null) {
									responseCallback = (JSBridgeCallback) responseCallbacks.get(responseId);
									if(responseCallback != null) {
										try {
											String data = getString(message,"responseData");
											responseCallback.callBack(((data != null)?(getJSONObject(data)):(null)));
											responseCallbacks.remove(responseId);
										} catch(Exception e) {
											e.printStackTrace();
										}
									}
								} else {
									final String callbackId = getString(message,"callbackId");
									class responseCallBackImpl implements JSBridgeCallback {
										@Override
										public void callBack(JSONObject data) {
											if(callbackId != null) {
												JSONObject msg = new JSONObject();
												updateJsonObject(msg, "responseId", callbackId);
												updateJsonObject(msg, "responseData", data);
												queueMessage(msg);
											}
										}
									}
									
									// if callbackId is null then empty callback will be created.
									responseCallback = new responseCallBackImpl();
									
									JSBridgeHandler hander = null;
									String eventName = getString(message,"eventName");
									if(eventName != null) {
										hander = (JSBridgeHandler) messageHandlers.get(eventName);
									}									
									if(hander != null) {
										hander.hanlder(message.getJSONObject("data"), responseCallback);
									} else {
										if(jsBridgeHandler != null) {
											JSONObject data = new JSONObject();
											Object obj = message.opt("data");
											if(obj != null) {
												data.put("data", obj);
											}
											jsBridgeHandler.hanlder(data,responseCallback);
										}
									}
								}
							}
						}
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}			
		}
		@JavascriptInterface
		public void Log(String str) {
			JSBridge.Log(str);
		}
		@JavascriptInterface
		public void LogArray(String str) {
			JSONObject obj;
			try {
				obj = new JSONObject(str);
				JSBridge.Log("LogArray: "+obj);
			} catch (JSONException e) {
				JSBridge.Log("LogArray: EXCEPTION");
				e.printStackTrace();
			}
		}		
	}
	
    private void dispatchMessage(final String jsonStr) {
		webView.post(new Runnable() {
		    @Override
		    public void run() {
		    	webView.loadUrl("javascript: "+JS_BRIDGE+"."+JS_BRIDGE_SEND_NATIVE_QUEUE+"('"+jsonStr+"');");
		    }
		});
    }

    private void queueMessage(JSONObject jsonObj) {
    	if(startupMessageQueue != null) {
    		startupMessageQueue.put(jsonObj);
    	} else {
    		dispatchMessage(getString(jsonObj));
    	}
    }
		
	public JSBridge(Object jsListener, Context context, WebView webview) {
		this.context = context;
		this.webView = webview;
		if(JSBridgeInteface.class.isInstance(jsListener)) {
			this.jsListener = (JSBridgeInteface)jsListener;
		}
		if(JSBridgeHandler.class.isInstance(jsListener)) {
			this.jsBridgeHandler = (JSBridgeHandler)jsListener;
		}
		if(webView != null) {
			registerJavaScriptAPI(new AndroidAPI(context, webview));
			webView.getSettings().setJavaScriptEnabled(true);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				WebView.setWebContentsDebuggingEnabled(true);
			}
			webView.setWebViewClient(new JSBridgeWebViewClient());
			webView.loadUrl("javascript: "+getAssetFileContents(JS_BRIDGE_FILE_NAME));
		}
	}
	
	public void registerJavaScriptAPI(JSBridgeAPIBase instance) {
		if(webView != null) {
			webView.addJavascriptInterface(instance, instance.getClass().getSimpleName());
		}
	}

	public void loadHTML(String url) {
		if(webView != null) {
			webView.loadUrl(url);
		}
	}
		
	public void send(String eventName, JSONObject data, JSBridgeCallback responseCallback) {
    	JSONObject message = new JSONObject();
    	updateJsonObject(message,"data",data);
    	updateJsonObject(message,"eventName",eventName);
    	if(responseCallback != null) {
        	String callbackId = "android_cb_"+(++uniqueId);
        	updateJsonObject(responseCallbacks,callbackId,responseCallback);
       		updateJsonObject(message,"callbackId",callbackId);
    	}    	
    	queueMessage(message);
	}
	
	public void registerEvent(String eventName, JSBridgeHandler hanlder) {
		try {
			messageHandlers.put(eventName, hanlder);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	public void deRegisterEvent(String eventName, JSBridgeHandler hanlder) {
		messageHandlers.remove(eventName);
	}
}