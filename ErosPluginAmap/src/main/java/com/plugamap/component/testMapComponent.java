package com.plugamap.component;

import android.widget.FrameLayout;

import com.alibaba.weex.plugin.annotation.WeexComponent;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.taobao.weex.WXSDKInstance;
import com.taobao.weex.dom.WXDomObject;
import com.taobao.weex.ui.component.WXVContainer;
import com.amap.api.maps.LocationSource;
@WeexComponent(names = {"my-amap"})
public class testMapComponent extends WXVContainer<FrameLayout> implements LocationSource,AMapLocationListener {


    public testMapComponent(WXSDKInstance instance, WXDomObject node, WXVContainer parent) {
        super(instance, node, parent);
    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {

    }

    @Override
    public void deactivate() {

    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {

    }
}
