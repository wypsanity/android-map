package com.plugamap.component;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;
import com.alibaba.weex.plugin.annotation.WeexComponent;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapOptions;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.VisibleRegion;
import com.amap.api.maps.model.animation.Animation;
import com.amap.api.maps.model.animation.TranslateAnimation;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.plugamap.R;
import com.plugamap.util.Constant;
import com.taobao.weex.WXSDKInstance;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.dom.WXDomObject;
import com.taobao.weex.ui.component.WXComponentProp;
import com.taobao.weex.ui.component.WXVContainer;
import com.taobao.weex.ui.view.WXFrameLayout;
import com.taobao.weex.utils.WXLogUtils;
import com.taobao.weex.utils.WXViewUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

@WeexComponent(names = {"weex-amap"})
public class WXMapViewComponent extends WXVContainer<FrameLayout> implements LocationSource,
    AMapLocationListener, GeocodeSearch.OnGeocodeSearchListener, PoiSearch.OnPoiSearchListener, AdapterView.OnItemClickListener {
    private static final String TAG = "WXMapViewComponent";
    private static final int REQUEST_CODE_MAPVIEW = 0xA;
    private static String[] permissions = new String[]{
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS"
    };
    private TextureMapView mMapView;
    private AMap mAMap;
    private UiSettings mUiSettings;
    private Activity mActivity;

    private boolean isScaleEnable = true;
    private boolean isZoomEnable = true;
    private boolean isCompassEnable = true;
    private boolean isMyLocationEnable = false;
    private float mZoomLevel;
    private int mGesture = 0xF;
    private boolean isIndoorSwitchEnable = false;
    private OnLocationChangedListener mLocationChangedListener;
    private AMapLocationClient mLocationClient;
    private AMapLocationClientOption mLocationOption;
    private HashMap<String, WXMapInfoWindowComponent> mInfoWindowHashMap = new HashMap<>();
    private AtomicBoolean isMapLoaded = new AtomicBoolean(false);
    private AtomicBoolean isInited = new AtomicBoolean(false);
    private Queue<MapOperationTask> paddingTasks = new LinkedList<>();
    private FrameLayout mapContainer;
    private int fakeBackgroundColor = Color.rgb(242, 238, 232);
    private Marker locationMarker;
    private ProgressDialog progDialog = null;
    private LatLonPoint searchLatlonPoint;
    private GeocodeSearch geocoderSearch;
    private PoiSearch.Query query;// Poi查询条件类
    private int currentPage = 0;// 当前页面，从0开始计数
    private PoiSearch poiSearch;
    private String[] items = {"住宅区", "学校", "楼宇", "商场" };
    private String searchType = items[0];
    private String searchKey = "";
    private PoiItem firstItem;
    private List<PoiItem> poiItems;// poi数据
    private List<PoiItem> resultData;
    //private SearchResultAdapter searchResultAdapter;
    private ListView listView;
    public WXMapViewComponent(WXSDKInstance instance, WXDomObject dom, WXVContainer parent, boolean isLazy) {
        super(instance, dom, parent, isLazy);
    }

    @Override
    protected FrameLayout initComponentHostView(@NonNull Context context) {
        mapContainer = new FrameLayout(context);
        mapContainer.setBackgroundColor(fakeBackgroundColor);
        if (context instanceof Activity) {
            mActivity = (Activity) context;
        }
        return mapContainer;
    }

    @Override
    protected void setHostLayoutParams(FrameLayout host, int width, int height, int left, int right, int top, int bottom) {
        super.setHostLayoutParams(host, width, height, left, right, top, bottom);
        if (!isMapLoaded.get() && !isInited.get()) {
            isInited.set(true);
            mapContainer.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mMapView = new TextureMapView(getContext());
                    mapContainer.addView(mMapView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                    WXLogUtils.e(TAG, "Create MapView " + mMapView.toString());
                    initMap();
                }
            }, 0);
        }
    }

    private void initMap() {
        mMapView.onCreate(null);
        isMapLoaded.set(false);
        if (mAMap == null) {
            mAMap = mMapView.getMap();

            mAMap.setInfoWindowAdapter(new InfoWindowAdapter(this));
            mAMap.setOnMapLoadedListener(new AMap.OnMapLoadedListener() {
                @Override
                public void onMapLoaded() {
                    WXLogUtils.e(TAG, "Map loaded");
                    isMapLoaded.set(true);
                    mZoomLevel = mAMap.getCameraPosition().zoom;
                    mMapView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            execPaddingTasks();
                        }
                    }, 16);
                    addMarkerInScreenCenter(null);
                }
            });

            // 绑定 Marker 被点击事件
            mAMap.setOnMarkerClickListener(new AMap.OnMarkerClickListener() {
                // marker 对象被点击时回调的接口
                // 返回 true 则表示接口已响应事件，否则返回false
                @Override
                public boolean onMarkerClick(Marker marker) {

                    if (marker != null) {
                        for (int i = 0; i < getChildCount(); i++) {
                            if (getChild(i) instanceof WXMapMarkerComponent) {
                                WXMapMarkerComponent child = (WXMapMarkerComponent) getChild(i);
                                if (child.getMarker() != null && child.getMarker().getId() == marker.getId()) {
                                    child.onClick();
                                }
                            }
                        }
                    }
                    return false;
                }
            });
            mAMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {

                private boolean mZoomChanged;

                @Override
                public void onCameraChange(CameraPosition cameraPosition) {
                    mZoomChanged = mZoomLevel != cameraPosition.zoom;
                    mZoomLevel = cameraPosition.zoom;
                }

                @Override
                public void onCameraChangeFinish(CameraPosition cameraPosition) {
                    if (mZoomChanged) {

                        float scale = mAMap.getScalePerPixel();
                        float scaleInWeex = scale / WXViewUtils.getWeexPxByReal(scale);

                        VisibleRegion visibleRegion = mAMap.getProjection().getVisibleRegion();
                        WXLogUtils.d(TAG, "Visible region: " + visibleRegion.toString());
                        Map<String, Object> region = new HashMap<>();
                        region.put("northeast", convertLatLng(visibleRegion.latLngBounds.northeast));
                        region.put("southwest", convertLatLng(visibleRegion.latLngBounds.southwest));

                        Map<String, Object> data = new HashMap<>();
                        data.put("targetCoordinate", cameraPosition.target.toString());
                        data.put("zoom", cameraPosition.zoom);
                        data.put("tilt", cameraPosition.tilt);
                        data.put("bearing", cameraPosition.bearing);
                        data.put("isAbroad", cameraPosition.isAbroad);
                        data.put("scalePerPixel", scaleInWeex);
                        data.put("visibleRegion", region);
                        getInstance().fireEvent(getRef(), Constant.EVENT.ZOOM_CHANGE, data);
                    }
                    searchLatlonPoint = new LatLonPoint(cameraPosition.target.latitude, cameraPosition.target.longitude);
                    geoAddress();
                    startJumpAnimation();
                }
            });

            mAMap.setOnMapTouchListener(new AMap.OnMapTouchListener() {
                boolean dragged = false;

                @Override
                public void onTouch(MotionEvent motionEvent) {

                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_MOVE:
                            dragged = true;
                            break;
                        case MotionEvent.ACTION_UP:
                            if (dragged) getInstance().fireEvent(getRef(), Constant.EVENT.DRAG_CHANGE);
                            dragged = false;
                            break;
                    }
                }
            });
            setUpMap();
        }
        progDialog = new ProgressDialog(getContext());
        geocoderSearch = new GeocodeSearch(getContext());
        geocoderSearch.setOnGeocodeSearchListener(this);

    }

    //dip和px转换
    private static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 响应地址地理编码
     */
    public void geoAddress() {
        showDialog();
        //        searchText.setText("");
        if (searchLatlonPoint != null){
            RegeocodeQuery query = new RegeocodeQuery(searchLatlonPoint, 200, GeocodeSearch.AMAP);// 第一个参数表示一个Latlng，第二参数表示范围多少米，第三个参数表示是火系坐标系还是GPS原生坐标系
            geocoderSearch.getFromLocationAsyn(query);
        }
    }
    public void dismissDialog() {
        if (progDialog != null) {
            progDialog.dismiss();
        }
    }
    public void showDialog() {
        progDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progDialog.setIndeterminate(false);
        progDialog.setCancelable(true);
        progDialog.setMessage("正在加载...");
        progDialog.show();
    }
    public void startJumpAnimation() {

        if (locationMarker != null ) {
            //根据屏幕距离计算需要移动的目标点
            final LatLng latLng = locationMarker.getPosition();
            Point point =  mAMap.getProjection().toScreenLocation(latLng);
            point.y -= dip2px(getContext(),125);
            LatLng target = mAMap.getProjection()
                .fromScreenLocation(point);
            //使用TranslateAnimation,填写一个需要移动的目标点
            Animation animation = new TranslateAnimation(target);
            animation.setInterpolator(new Interpolator() {
                @Override
                public float getInterpolation(float input) {
                    // 模拟重加速度的interpolator
                    if(input <= 0.5) {
                        return (float) (0.5f - 2 * (0.5 - input) * (0.5 - input));
                    } else {
                        return (float) (0.5f - Math.sqrt((input - 0.5f)*(1.5f - input)));
                    }
                }
            });
            //整个移动所需要的时间
            animation.setDuration(600);
            //设置动画
            locationMarker.setAnimation(animation);
            //开始动画
            locationMarker.startAnimation();

        } else {
            Log.e("ama","screenMarker is null");
        }
    }
    private void addMarkerInScreenCenter(LatLng locationLatLng) {
        LatLng latLng = mAMap.getCameraPosition().target;
        Point screenPosition = mAMap.getProjection().toScreenLocation(latLng);
        locationMarker = mAMap.addMarker(new MarkerOptions()
            .anchor(0.5f,0.5f)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.purple_pin)));
        //设置Marker在屏幕上,不跟随地图移动
        locationMarker.setPositionByPixels(screenPosition.x,screenPosition.y);
        locationMarker.setZIndex(1);

    }
    private void setUpMap() {
        mUiSettings = mAMap.getUiSettings();

        mUiSettings.setScaleControlsEnabled(isScaleEnable);
        mUiSettings.setZoomControlsEnabled(isZoomEnable);
        mUiSettings.setCompassEnabled(isCompassEnable);
        mUiSettings.setIndoorSwitchEnabled(isIndoorSwitchEnable);
        if (checkPermissions(mActivity, permissions)) {
            setMyLocationStatus(isMyLocationEnable);
        }
        updateGestureSetting();

    }

    private void updateGestureSetting() {
        if ((mGesture & 0xF) == 0xF) {
            mUiSettings.setAllGesturesEnabled(true);
        } else {
            if ((mGesture & Constant.Value.SCROLLGESTURE) == Constant.Value.SCROLLGESTURE) {
                mUiSettings.setScrollGesturesEnabled(true);
            } else {
                mUiSettings.setScrollGesturesEnabled(false);
            }

            if ((mGesture & Constant.Value.ZOOMGESTURE) == Constant.Value.ZOOMGESTURE) {
                mUiSettings.setZoomGesturesEnabled(true);
            } else {
                mUiSettings.setZoomGesturesEnabled(false);
            }

            if ((mGesture & Constant.Value.TILTGESTURE) == Constant.Value.TILTGESTURE) {
                mUiSettings.setTiltGesturesEnabled(true);
            } else {
                mUiSettings.setTiltGesturesEnabled(false);
            }

            if ((mGesture & Constant.Value.ROTATEGESTURE) == Constant.Value.ROTATEGESTURE) {
                mUiSettings.setRotateGesturesEnabled(true);
            } else {
                mUiSettings.setRotateGesturesEnabled(false);
            }
        }
        WXLogUtils.e(TAG, "init map end ");
    }

    @JSMethod
    public void setMyLocationButtonEnabled(boolean enabled) {

        if (mUiSettings != null) {
            mUiSettings.setMyLocationButtonEnabled(enabled);
        }
    }
    @JSMethod
    public void setSelectOption(int i,@Nullable final JSCallback callback) {
        switch (i){
            case 1 :
                searchType = items[0];
                break;
            case 2 :
                searchType = items[1];
                break;
            case 3 :
                searchType = items[2];
                break;
            case 4 :
                searchType = items[3];
                break;
        }
        geoAddress();
    }
    @Override
    public void onActivityCreate() {
        super.onActivityCreate();
        WXLogUtils.e(TAG, "onActivityCreate");
    }

    @Override
    public void onActivityPause() {
        if (mMapView != null) {
            mMapView.onPause();
            deactivate();
        }
        WXLogUtils.e(TAG, "onActivityPause");
    }

    @Override
    public void onActivityResume() {
        if (mMapView != null) {
            mMapView.onResume();
        }
        WXLogUtils.e(TAG, "onActivityResume");
    }

    private boolean requestPermissions() {
        boolean granted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            granted = false;
            if (mActivity != null) {
                if (!checkPermissions(mActivity, permissions)) {
                    ActivityCompat.requestPermissions(mActivity, permissions, REQUEST_CODE_MAPVIEW);
                } else {
                    granted = true;
                }
            }
        }
        return granted;
    }

    @Override
    public void onActivityDestroy() {
        if (mMapView != null) {
            mMapView.onDestroy();
        }
        if (mLocationClient != null) {
            mLocationClient.onDestroy();
        }
        WXLogUtils.e(TAG, "onActivityDestroy");
    }

    @WXComponentProp(name = Constant.Name.KEYS)
    public void setApiKey(String keys) {
        try {
            JSONObject object = new JSONObject(keys);
            String key = object.optString("android");
            if (!TextUtils.isEmpty(key)) {
                MapsInitializer.setApiKey(key);
                AMapLocationClient.setApiKey(key);
                //ServiceSettings.getInstance().setApiKey(key);
                WXLogUtils.d(TAG, "Set API key success");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    @WXComponentProp(name = Constant.Name.SCALECONTROL)
    public void setScaleEnable(final boolean scaleEnable) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                isScaleEnable = scaleEnable;
                mUiSettings.setScaleControlsEnabled(scaleEnable);
            }
        });
    }

    @WXComponentProp(name = Constant.Name.ZOOM_ENABLE)
    public void setZoomEnable(final boolean zoomEnable) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                isZoomEnable = zoomEnable;
                mUiSettings.setZoomControlsEnabled(zoomEnable);
            }
        });
    }

    @WXComponentProp(name = Constant.Name.ZOOM)
    public void setZoom(final int level) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                mAMap.moveCamera(CameraUpdateFactory.zoomTo(level));
            }
        });
    }

    @WXComponentProp(name = Constant.Name.COMPASS)
    public void setCompass(final boolean compass) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                isCompassEnable = compass;
                mUiSettings.setCompassEnabled(compass);
            }
        });
    }

    @WXComponentProp(name = Constant.Name.GEOLOCATION)
    public void setMyLocationEnable(final boolean myLocationEnable) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                isMyLocationEnable = myLocationEnable;
                if (requestPermissions()) {
                    setMyLocationStatus(myLocationEnable);
                }
            }
        });
    }

    @WXComponentProp(name = Constant.Name.ZOOM_POSITION)
    public void setZoomPosition(final String position) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                if (mUiSettings != null) {
                    if (Constant.Value.RIGHT_BOTTOM.equalsIgnoreCase(position)) {
                        mUiSettings.setZoomPosition(AMapOptions.ZOOM_POSITION_RIGHT_BUTTOM);
                    } else if (Constant.Value.RIGHT_CENTER.equalsIgnoreCase(position)) {
                        mUiSettings.setZoomPosition(AMapOptions.ZOOM_POSITION_RIGHT_CENTER);
                    } else {
                        WXLogUtils.e(TAG, "Illegal zoom position value: " + position);
                    }
                }
            }
        });
    }

    @Override
    public void addSubView(View child, int index) {

    }

    @WXComponentProp(name = Constant.Name.CENTER)
    public void setCenter(final String location) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                try {
                    JSONArray jsonArray = new JSONArray(location);
                    LatLng latLng = new LatLng(jsonArray.optDouble(1), jsonArray.optDouble(0));
                    mAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng,16f));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @WXComponentProp(name = Constant.Name.GESTURE)
    @Deprecated
    public void setGesture(final int gesture) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                mGesture = gesture;
                updateGestureSetting();
            }
        });
    }

    @WXComponentProp(name = Constant.Name.GESTURES)
    public void setGestures(final String gestures) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                try {
                    WXLogUtils.d(TAG, "setGestures: " + gestures);
                    JSONArray array = new JSONArray(gestures);
                    mUiSettings.setAllGesturesEnabled(false);
                    for (int i = 0; i < array.length(); i++) {
                        String gesture = array.getString(i);
                        if ("zoom".equalsIgnoreCase(gesture)) {
                            mUiSettings.setZoomGesturesEnabled(true);
                        } else if ("rotate".equalsIgnoreCase(gesture)) {
                            mUiSettings.setRotateGesturesEnabled(true);
                        } else if ("tilt".equalsIgnoreCase(gesture)) {
                            mUiSettings.setTiltGesturesEnabled(true);
                        } else if ("scroll".equalsIgnoreCase(gesture)) {
                            mUiSettings.setScrollGesturesEnabled(true);
                        } else {
                            WXLogUtils.w(TAG, "Wrong gesture: " + gesture);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @WXComponentProp(name = Constant.Name.MY_LOCATION_ENABLED)
    public void setMyLocationEnabled(final boolean enabled) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                WXLogUtils.d(TAG, "setMyLocationButtonEnabled: " + enabled);
                mUiSettings.setMyLocationButtonEnabled(enabled);
            }
        });
    }

    @WXComponentProp(name = Constant.Name.SHOW_MY_LOCATION)
    public void setShowMyLocation(final boolean show) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                WXLogUtils.d(TAG, "setShowMyLocation: " + show);
                MyLocationStyle style = mAMap.getMyLocationStyle();
                if (style == null) {
                    style = new MyLocationStyle();
                }
                style.showMyLocation(show);
                mAMap.setMyLocationStyle(style);
            }
        });
    }

    @WXComponentProp(name = Constant.Name.CUSTOM_ENABLED)
    public void setCustomEnabled(final boolean enabled) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                WXLogUtils.d(TAG, "setMapCustomEnable: " + enabled);
                mAMap.setMapCustomEnable(true);
            }
        });
    }

    @WXComponentProp(name = Constant.Name.CUSTOM_STYLE_PATH)
    public void setCustomStylePath(final String pathObject) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                try {
                    JSONObject object = new JSONObject(pathObject);
                    String path = object.optString("android");
                    if (!TextUtils.isEmpty(path)) {
                        WXLogUtils.d(TAG, "setCustomMapStylePath: " + path);
                        mAMap.setCustomMapStylePath(path);
                        mAMap.setMapCustomEnable(true);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @WXComponentProp(name = Constant.Name.INDOORSWITCH)
    public void setIndoorSwitchEnable(final boolean indoorSwitchEnable) {
        postTask(new MapOperationTask() {
            @Override
            public void execute(TextureMapView mapView) {
                isIndoorSwitchEnable = indoorSwitchEnable;
                mUiSettings.setIndoorSwitchEnabled(indoorSwitchEnable);
            }
        });
    }

    public void setMyLocationStatus(boolean isActive) {
        if (isActive) {
            mAMap.setLocationSource(this);// 设置定位监听
            mUiSettings.setMyLocationButtonEnabled(true && checkPermissions(mActivity, permissions));// 设置默认定位按钮是否显示
            mAMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
            // 设置定位的类型为定位模式 ，可以由定位、跟随或地图根据面向方向旋转几种
            mAMap.setMyLocationType(AMap.LOCATION_TYPE_LOCATE);
        } else {
            deactivate();
            mAMap.setLocationSource(null);
            mAMap.setMyLocationEnabled(false);
            mUiSettings.setMyLocationButtonEnabled(false);
        }
    }

    @Override
    public void activate(OnLocationChangedListener listener) {
        mLocationChangedListener = listener;
        if (mLocationClient == null) {
            mLocationClient = new AMapLocationClient(getContext());
            mLocationOption = new AMapLocationClientOption();
            //设置定位监听
            mLocationClient.setLocationListener(this);
            //设置为高精度定位模式
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //设置定位参数
            mLocationClient.setLocationOption(mLocationOption);
            // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
            // 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用stopLocation()方法来取消定位请求
            // 在定位结束后，在合适的生命周期调用onDestroy()方法
            // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
            mLocationClient.startLocation();
        }
    }

    @Override
    public void deactivate() {
        mLocationChangedListener = null;
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
            mLocationClient.onDestroy();
        }
        mLocationClient = null;
    }

    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (mLocationChangedListener != null && amapLocation != null) {
            if (amapLocation != null && amapLocation.getErrorCode() == 0) {

                mLocationChangedListener.onLocationChanged(amapLocation);// 显示系统小蓝点
                LatLng curLatlng = new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude());
                searchLatlonPoint = new LatLonPoint(curLatlng.latitude, curLatlng.longitude);
            } else {
                String errText = "定位失败," + amapLocation.getErrorCode() + ": " + amapLocation.getErrorInfo();
                WXLogUtils.e("AmapErr", errText);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_MAPVIEW:
                if (checkPermissions(mActivity, permissions) && isMyLocationEnable) {
                    setMyLocationEnable(isMyLocationEnable);
                }
                break;
            default:
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean checkPermissions(Activity context, String[] permissions) {
        boolean granted = true;
        if (permissions != null && permissions.length > 0) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    if (ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                        Toast.makeText(context, "please give me the permissions", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        return granted;
    }

    public HashMap<String, WXMapInfoWindowComponent> getCachedInfoWindow() {
        return mInfoWindowHashMap;
    }

    private void execPaddingTasks() {
        while (!paddingTasks.isEmpty()) {
            MapOperationTask task = paddingTasks.poll();
            if (task != null && mMapView != null) {
                WXLogUtils.d(TAG, "Exec padding task " + task.toString());
                task.execute(mMapView);
            }
        }
    }
    protected void doSearchQuery() {
        //        Log.i("MY", "doSearchQuery");
        currentPage = 0;
        query = new PoiSearch.Query(searchKey, searchType, "");// 第一个参数表示搜索字符串，第二个参数表示poi搜索类型，第三个参数表示poi搜索区域（空字符串代表全国）
        query.setCityLimit(true);
        query.setPageSize(20);
        query.setPageNum(currentPage);

        if (searchLatlonPoint != null) {
            poiSearch = new PoiSearch(getContext(), query);
            poiSearch.setOnPoiSearchListener(this);
            poiSearch.setBound(new PoiSearch.SearchBound(searchLatlonPoint, 1000, true));//
            poiSearch.searchPOIAsyn();
        }
    }

    public void postTask(MapOperationTask task) {
        if (mMapView != null && isMapLoaded.get()) {
            WXLogUtils.d(TAG, "Exec task " + task.toString());
            task.execute(mMapView);
        } else {
            WXLogUtils.d(TAG, "Padding task " + task.toString());
            paddingTasks.offer(task);
        }
    }

    @Override
    public void onRegeocodeSearched(RegeocodeResult result, int rCode) {
        dismissDialog();
        if (rCode == AMapException.CODE_AMAP_SUCCESS) {
            if (result != null && result.getRegeocodeAddress() != null
                && result.getRegeocodeAddress().getFormatAddress() != null) {
                String address = result.getRegeocodeAddress().getProvince() + result.getRegeocodeAddress().getCity() + result.getRegeocodeAddress().getDistrict() + result.getRegeocodeAddress().getTownship();
                firstItem = new PoiItem("regeo", searchLatlonPoint, address, result.getRegeocodeAddress().getTownship());
                firstItem.setProvinceName(result.getRegeocodeAddress().getProvince());
                firstItem.setCityName(result.getRegeocodeAddress().getCity());
                firstItem.setAdName(result.getRegeocodeAddress().getDistrict());
                doSearchQuery();
                //HashMap<String, Object> hashmap = new HashMap<String, Object>();
                //hashmap.put("data",address);
                //fireEvent("finish", hashmap);
            }
        } else {
            Toast.makeText(getContext(), "error code is " + rCode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int i) {

    }

    /**
     * 搜索的监听器
     */
    @Override
    public void onPoiSearched(PoiResult poiResult, int i) {
        if (i == AMapException.CODE_AMAP_SUCCESS) {
            if (poiResult != null && poiResult.getQuery() != null) {
                if (poiResult.getQuery().equals(query)) {
                    poiItems = poiResult.getPois();
                    if (poiItems != null && poiItems.size() > 0) {
                        //updateListview(poiItems);
                        HashMap<String, String> obj = null;
                        ArrayList<HashMap<String, String>> array = new ArrayList<>();
                        if(firstItem!=null) {
                            obj = new HashMap<>();
                            String title = firstItem.getTitle();
                            String address = firstItem.getProvinceName()+firstItem.getCityName()+firstItem.getAdName()+"-"+firstItem.getSnippet();
                            obj.put("title",title);
                            obj.put("address",address);
                            array.add(obj);
                        }
                        for (PoiItem poiItem:poiItems) {
                            obj = new HashMap<>();
                            String title = poiItem.getTitle();
                            String address = poiItem.getProvinceName()+poiItem.getCityName()+ poiItem.getAdName() +"-"+ poiItem.getSnippet();
                            obj.put("title",title);
                            obj.put("address",address);
                            array.add(obj);
                        }
                        HashMap<String, Object> hashmap = new HashMap<String, Object>();
                        hashmap.put("data",array);
                        fireEvent("finish", hashmap);
                    } else {
                        Toast.makeText(getContext(), "无搜索结果", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(getContext(), "无搜索结果", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {

    }
    //view的Listener
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

    }

    private static class InfoWindowAdapter implements AMap.InfoWindowAdapter {

        private WXMapViewComponent mWXMapViewComponent;

        InfoWindowAdapter(WXMapViewComponent wxMapViewComponent) {
            mWXMapViewComponent = wxMapViewComponent;
        }

        @Override
        public View getInfoWindow(Marker marker) {
            return render(marker);
        }

        @Override
        public View getInfoContents(Marker marker) {
            return render(marker);
        }

        private View render(Marker marker) {
            WXMapInfoWindowComponent wxMapInfoWindowComponent = mWXMapViewComponent.mInfoWindowHashMap.get(marker.getId());
            if (wxMapInfoWindowComponent != null) {
                WXFrameLayout host = wxMapInfoWindowComponent.getHostView();
                host.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                host.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                WXLogUtils.d(TAG, "Info size: " + host.getMeasuredWidth() + ", " + host.getMeasuredHeight());
                return host;
            } else {
                WXLogUtils.e(TAG, "WXMapInfoWindowComponent with marker id " + marker.getId() + " not found");
            }
            return null;
        }
    }

    private Map<String, Object> convertLatLng(LatLng latLng) {
        Map<String, Object> result = new HashMap<>(2);
        result.put("latitude", latLng.latitude);
        result.put("longitude", latLng.longitude);
        return result;
    }

    interface MapOperationTask {
        void execute(TextureMapView mapView);
    }
}
