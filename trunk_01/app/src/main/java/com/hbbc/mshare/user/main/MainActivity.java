package com.hbbc.mshare.user.main;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapOptions;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.Projection;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.bumptech.glide.Glide;
import com.hbbc.R;
import com.hbbc.mshare.login.LoginResultBean;
import com.hbbc.mshare.login.UserDao;
import com.hbbc.mshare.user.AuthenticationActivity;
import com.hbbc.mshare.user.DepositActivity;
import com.hbbc.mshare.user.LoginActivity;
import com.hbbc.mshare.user.ScanActivity;
import com.hbbc.mshare.user.UserActivity;
import com.hbbc.mshare.user.search.SearchActivity;
import com.hbbc.mshare.user.search.SearchBean;
import com.hbbc.util.AMapUtil;
import com.hbbc.util.BaseActivity;
import com.hbbc.util.Constants;
import com.hbbc.util.CustomWalkRouteOverlay;
import com.hbbc.util.GlobalConfig;
import com.hbbc.util.HttpUtil;
import com.hbbc.util.MyPopupWindow;
import com.hbbc.util.MyRelativeLayout;
import com.hbbc.util.MyTopbar;
import com.hbbc.util.ToastUtil;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import java.util.List;

/**
 * Created by Administrator on 2017/7/4.
 * 判断下: 1. 如果是登陆的三个过程全部完成了,那么就不要再显示出PopupWindow中的 <注册Button></注册Button>了.
 *          2.如果有一项没有完成,那么就仍然要在Popup_window中显示出这个注册按钮.
 *    TODO   3.上面的这个判断,先放在每次弹出popup_window时,读取一次数据库,判断一次; 如果效果太差,就每次启动应用时,初始化一次
 */
public class MainActivity extends BaseActivity implements LocationSource,
        AMapLocationListener, MyTopbar.OnTopBarClickListener, AMap.OnMarkerClickListener,
        RouteSearch.OnRouteSearchListener, MyRelativeLayout.OnPopupWindowGoneListener {

    private static final String TAG = "MainActivity";

    private static final int STROKE_COLOR = Color.argb(180, 3, 145, 255);

    private static final int FILL_COLOR = Color.argb(10, 0, 0, 180);

    private static final int REQUESTCODE = 101;//向搜索页面发送的请求码

    private static final String SELECTED_ITEM = "selected_item";

    private static final int ROUTE_TYPE_WALK = 3;

    private static final int AUTOLOGIN = 1;//自动登陆的what字段值

    private static final int GET_PRODUCTS_FROM_SERVER = 2;//获取当前定位点附近的物品的what字段值

    private static final int MY_PERMISSION_REQUEST_FINE_LOCATION = 0xffff;

    private static final int REQUEST_CAMERA_PERMISSION = 0x0fff;

    private MapView mapView;

    private AMap aMap;

    private OnLocationChangedListener mListener;

    private AMapLocationClient mlocationClient;

    private AMapLocationClientOption mLocationOption;

    private MyPopupWindow popupWindow;

    //116.184419,39.928728

    private HttpUtil httpUtil;

    private List<ResultBean.Goods> goodsList;//返回的附近物品对象的集合

    private LatLng currentLatLng;//当前定位到的位置的坐标对象

    private RouteSearch routeSearch;

    private WalkRouteResult mWalkRouteResult;//做为一个成员变量,方便使用返回路线结果对象

    private MyRelativeLayout parent;

    private ProgressDialog pd;

    private CustomWalkRouteOverlay walkRouteOverlay;

    private String currentAddress;

    private ResultBean.Goods selectedGoods;

    private ImageView centerIcon;


    /**
     * 接收服务器返回的数据,并显示
     */
    @Override
    protected void getMessage(Message msg) {

        switch (msg.what){
            case AUTOLOGIN:
                LoginResultBean resultBean= (LoginResultBean) msg.obj;
                handleLoginResult(resultBean);
                break;
            case GET_PRODUCTS_FROM_SERVER:
                ResultBean result = (ResultBean) msg.obj;
                //绘制图层上图标
                goodsList = result.getGoodsList();
                addAllProductsMarkers();
                break;
        }
    }



    private void handleLoginResult(LoginResultBean resultBean) {

        if(resultBean==null||!resultBean.getResult())
            return;

        //把最新的登陆结果bean写入到本地数据库中
        UserDao dao = UserDao.getDaoInstance(this);
        Log.d(TAG, "handleLoginResult: AutoLoginResult==="+resultBean.toString());
        int id = dao.query().getId();
        resultBean.setId(id);
        int update = dao.update(resultBean);
        Log.d(TAG, "handleLoginResult: AutoLoginResult==="+update);

    }



    /**
     * 把服务器返回来的所有物品,添加到地图图层上显示,一个图标代表一个对象.
     */
    private void addAllProductsMarkers() {

        if (goodsList == null || goodsList.size() == 0)
            return;
        for (int i = 0; i < goodsList.size(); i++) {
            LatLng latLng = new LatLng(goodsList.get(i).getLat(), goodsList.get(i).getLng());
            MarkerOptions markerOption = new MarkerOptions();
            markerOption.position(latLng);
//            markerOption.title("西安市").snippet("西安市：34.341568, 108.940174");
//            markerOption.draggable(true);//设置Marker可拖动

            //////////////////////////
            //下面的 .icon()自带有清除已有图标的功能了,应该不会是此处添加了默认的位置图标
            //////////////////////////

            markerOption.icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory
                    .decodeResource(getResources(), R.drawable.mshare_excavator)));
            // 将Marker设置为贴地显示，可以双指下拉地图查看效果
//        markerOption.setFlat(true);//设置marker平贴地图效果
            Marker marker = aMap.addMarker(markerOption);
            marker.setObject(goodsList.get(i));

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.mshare_main);
        httpUtil = new HttpUtil();
//        requestPermission();
        initView(savedInstanceState);

        registerToWeixin();

    }


    //注册APP到微信客户端
    private void registerToWeixin() {

        IWXAPI api  = WXAPIFactory.createWXAPI(this,GlobalConfig.AppID,false);
        api.registerApp(GlobalConfig.WEIXIN_APP_ID);

    }



    private void initView(Bundle savedInstanceState) {

        initTopbar();
        initCenterIcon();
        popupWindow = (MyPopupWindow) findViewById(R.id.popup_window);
        parent = (MyRelativeLayout) findViewById(R.id.container);

        //实现伴随滑动
        parent.setOnViewPositionChangedListener(new MyRelativeLayout.OnViewPositionChangedListener() {
            @Override
            public void onViewPositionChanged(int dy) {

                if (aMap == null)
                    return;
                CameraUpdate cameraUpdate = CameraUpdateFactory.scrollBy(0, dy);
                aMap.moveCamera(cameraUpdate);
//                aMap.getUiSettings().setZoomGesturesEnabled(true);//TODO 有些浪费性能了,至少目前可以解决掉小概率出现的手势缩放失效的BUG
            }
        });
        parent.setOnSignUpClickListener(new MyRelativeLayout.OnSignUpClickListener() {
            @Override
            public void onClick(View v) {
                //二次判断
                LoginResultBean result = UserDao.getDaoInstance(GlobalConfig.APP_Context).query();
                Intent intent=new Intent();
                if(result==null)//当前没有默认登陆用户
                    intent.setClass(MainActivity.this,LoginActivity.class);
                else
                if(result.getOpenCertification()==1&&result.getCertificationStatus()!=1)
                    intent.setClass(MainActivity.this, AuthenticationActivity.class);
                else
                if(result.getOpenDesposit()==1&&result.getDespositStatus()==1)
                    intent.setClass(MainActivity.this, DepositActivity.class);
                else
                    return;

                startActivity(intent);
                overridePendingTransition(R.anim.global_in, 0);
            }
        });

        parent.setonPopupWindowGoneListener(this);

        ImageButton btn_rePosition = (ImageButton) findViewById(R.id.btn_position);
        TextView btn_scan = (TextView) findViewById(R.id.tv_scanning);

        btn_rePosition.setOnClickListener(this);
        btn_scan.setOnClickListener(this);

        mapView = (MapView) findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        initMapRelated();
    }



    /**
     * 动态改变红色图标的中心,将其上移自身高度的一半(*_*)
     */
    private void initCenterIcon() {

        centerIcon = (ImageView) findViewById(R.id.iv_center);
        centerIcon.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {

                int height = centerIcon.getHeight();
                Log.d(TAG, "onLayoutChange: height====" + height);
                centerIcon.setTranslationY(-height / 2);
                centerIcon.removeOnLayoutChangeListener(this);
            }
        });
    }

    private void reposition() {

        if (mListener == null) {
            Toast.makeText(MainActivity.this, "listener = null", Toast.LENGTH_SHORT).show();
            return;
        }
        //重新定位时,清除掉之前的规划路径图层
//        clearWalkRouteOverlay();
        clearAllLayer();//清除所有图层
        if (popupWindow.getVisibility() == View.VISIBLE)
            hidePopupWindowWithAnimation();//隐藏掉popup_window

        mlocationClient = new AMapLocationClient(MainActivity.this);
        mLocationOption = new AMapLocationClientOption();
        //设置定位监听
        mlocationClient.setLocationListener(MainActivity.this);
        //设置为高精度定位模式
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //设置定位参数
//            mLocationOption.setInterval(10 * 1000);
        mLocationOption.setOnceLocation(true);
        //关闭缓存机制
        mLocationOption.setLocationCacheEnable(false);
        mlocationClient.setLocationOption(mLocationOption);
        // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
        // 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用stopLocation()方法来取消定位请求
        // 在定位结束后，在合适的生命周期调用onDestroy()方法
        // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除

        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)){

                new AlertDialog.Builder(this).setCancelable(true).setTitle("定位权限不足,无法定位")
                        .setMessage("请到系统设置页面授予该应用定位权限").show();

            }else{//如果没有权限,并且用户之前选择了不再询问,此处就直接申请权限!
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSION_REQUEST_FINE_LOCATION);
            }
        }else{
            mlocationClient.startLocation();
        }
    }



    private void initTopbar() {

        MyTopbar myTopbar = (MyTopbar) findViewById(R.id.top_bar);
        myTopbar.setOnTopBarClickListener(this);
    }



    private void initMapRelated() {

        if (aMap == null) {
            aMap = mapView.getMap();
            setUpMap();
        }
        //此对象用于发起路径规划
        routeSearch = new RouteSearch(this);
        routeSearch.setRouteSearchListener(this);
        //发起一次规划
//        searchRouteResult(ROUTE_TYPE_WALK,RouteSearch.WALK_DEFAULT);

    }
    private void setUpMap() {

        aMap.setLocationSource(this);// 设置定位监听

        aMap.getUiSettings().setLogoPosition(AMapOptions.LOGO_POSITION_BOTTOM_RIGHT);
        aMap.getUiSettings().setMyLocationButtonEnabled(false);// 设置默认定位按钮是否显示
        aMap.getUiSettings().setZoomControlsEnabled(true);//把默认的放大缩小按钮去去掉
        aMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        aMap.getUiSettings().setScaleControlsEnabled(true);
        setupLocationStyle();
    }



    /**
     * 开始搜索路径规划方案
     * 发送规划请求接口
     */
    public void searchRouteResult(LatLonPoint endPoint, int routeType, int mode) {

        if (currentLatLng == null) {
            Toast.makeText(MainActivity.this, "定位中，稍后再试..", Toast.LENGTH_SHORT).show();
            return;
        }
        if (endPoint == null) {
            Toast.makeText(MainActivity.this, "终点未设置", Toast.LENGTH_SHORT).show();
        }
//        showProgressDialog();
        LatLonPoint startPoint = new LatLonPoint(currentLatLng.latitude, currentLatLng.longitude);
        RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(
                startPoint, endPoint);
        if (routeType == ROUTE_TYPE_WALK) {// 步行路径规划
            RouteSearch.WalkRouteQuery query = new RouteSearch.WalkRouteQuery(fromAndTo, mode);
            routeSearch.calculateWalkRouteAsyn(query);// 异步路径规划步行模式查询
        }
    }

    //请求规划路径的过程中弹出ProgressDialog,请求返回结果后,取消显示
    private void showProgressDialog() {
        //TODO 这个progressDialog需要优化
        pd = new ProgressDialog(this);
        pd.setCancelable(false);
        pd.setMessage("路径请求规划中...");
        pd.show();
    }



    @Override
    protected void onStart() {
        super.onStart();
        ToastUtil.toast_debug("重走onStart()");
    }
    /**
     * 读数据库,判断是否在popup_window中显示登陆Button
     */
    private boolean shouldButtonGoneAway() {

        boolean shouldGoneAway=true;

        LoginResultBean currentUser = UserDao.getDaoInstance(this).query();
        //按逻辑逐个判断 登陆-->认证-->押金缴纳 这三个流程是否完成!
        if(currentUser==null|| TextUtils.isEmpty(currentUser.getAutoLoginKey())
                ||(currentUser.getOpenCertification()==1&&currentUser.getCertificationStatus()!=1)
                ||(currentUser.getOpenDesposit()==1&&currentUser.getDespositStatus()==1))
            shouldGoneAway=false;//依然在Popup_Window中显示Button
        return shouldGoneAway;
    }


    private void setupLocationStyle() {
        // 自定义系统定位蓝点
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        // 自定义定位蓝点图标
        myLocationStyle.myLocationIcon(BitmapDescriptorFactory.
                fromResource(R.drawable.gps_point));
        // 自定义精度范围的圆形边框颜色
        myLocationStyle.strokeColor(STROKE_COLOR);
        //自定义精度范围的圆形边框宽度
        myLocationStyle.strokeWidth(5);

        // 设置圆形的填充颜色
        myLocationStyle.radiusFillColor(FILL_COLOR);
        // 将自定义的 myLocationStyle 对象添加到地图上
        aMap.setMyLocationStyle(myLocationStyle);
    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {

        mListener = onLocationChangedListener;
        if (mlocationClient == null) {
            mlocationClient = new AMapLocationClient(this);
            mLocationOption = new AMapLocationClientOption();
            mLocationOption.setLocationCacheEnable(true);//

            //设置定位监听
            mlocationClient.setLocationListener(this);
            //设置为高精度定位模式
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //设置定位参数
//            mLocationOption.setInterval(10 * 1000);
            mLocationOption.setOnceLocation(true);
            //关闭缓存机制
            mLocationOption.setLocationCacheEnable(false);
            mlocationClient.setLocationOption(mLocationOption);
            // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
            // 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用stopLocation()方法来取消定位请求
            // 在定位结束后，在合适的生命周期调用onDestroy()方法
            // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
                if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)){
                    //
                    new AlertDialog.Builder(this)
                            .setCancelable(true)
                            .setMessage("定位失败,请到系统设置页面授予此应用定位权限")
                            .show();
                    
                }else{
                    ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            MY_PERMISSION_REQUEST_FINE_LOCATION);
                }
            }else{
                mlocationClient.startLocation();
            }
        }
    }



    @Override
    public void deactivate() {
//        mListener = null;
//        if (mlocationClient != null) {
//            mlocationClient.stopLocation();
//            mlocationClient.onDestroy();
//        }
        mlocationClient = null;
    }



    /**
     * 在此得到定位的坐标结果 (lat,lng)
     */
    @Override
    public void onLocationChanged(AMapLocation amapLocation) {

        if (mListener != null && amapLocation != null) {
            changeCurrentAddress(amapLocation);
            if (amapLocation != null
                    && amapLocation.getErrorCode() == 0) {
                mListener.onLocationChanged(amapLocation);// 显示系统小蓝点
                currentLatLng = new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude());
                //摩拜单车也碰到了这个旋转抖动的问题: 只在第一次指定要旋转到的角度,以后的定位不再指定转动角度,按用户之前的旋转角度默认显示即可!
                aMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder().zoom(16f).tilt(30f).target(currentLatLng).build()));

                /**
                 * changeCamera(
                 * CameraUpdateFactory.newCameraPosition(new CameraPosition(
                 * Constants.SHANGHAI, 18, 30, 0)), this);
                 *
                 * aMap.animateCamera(update, 1000, callback);
                 */

                ToastUtil.toast("定位成功");
                //拿到定位到的坐标后,向服务器请求附近物品数据
                getProductsFromServer(currentLatLng);
            } else {
                String errText = "定位失败" + amapLocation.getErrorCode() + ": " + amapLocation.getErrorInfo();
                ToastUtil.toast(errText);
            }
        }

    }



    /**
     * 记录下此次定位到的当前位置在哪里
     */
    private void changeCurrentAddress(AMapLocation aMapLocation) {
        if(aMapLocation==null)
            return;
        StringBuilder sb=new StringBuilder();
        currentAddress = sb.append(aMapLocation.getCity())
                .append(aMapLocation.getDistrict())
                .append(aMapLocation.getStreet())
                .append(aMapLocation.getStreetNum())
                .toString();
    }

    /**
     * 按定位后的坐标向服务器请求附近共享物品的信息
     * TODO 当然,以后要加上限制定位的频次的逻辑
     */
    private void getProductsFromServer(LatLng latLng) {

        //116.184419,39.928728
        httpUtil.callJson(handler, GET_PRODUCTS_FROM_SERVER, this, GlobalConfig.MSHARE_SERVER_ROOT+GlobalConfig.MSHARE_RETRIEVE_ALL_PRODUCTS_AROUND,
                ResultBean.class, Constants.ECID, GlobalConfig.ECID, Constants.Lat, String.valueOf(latLng.latitude), Constants.Lng, String.valueOf(latLng.longitude));
        aMap.setOnMarkerClickListener(this);

    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        mapView.onDestroy();

        if (null != mlocationClient) {
            mlocationClient.onDestroy();
        }
    }

    @Override
    protected void onResume() {
        ToastUtil.toast_debug("执行了onResume()");
        super.onResume();
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mapView.onResume();

        //TODO 在此判断,如果用户已经完成了三个流程,那么不再显示popup_view中的注册登陆按钮
        if(shouldButtonGoneAway()) {
            parent.changeBottomButtonStatus(true);
        }else{
            parent.changeBottomButtonStatus(false);
        }
    }

    @Override
    protected void onPause() {

        super.onPause();
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mapView.onPause();
        //暂停定位服务
        deactivate();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mapView.onSaveInstanceState(outState);
    }


    /**
     * topBar上左右两个图标的点击回调接口
     */
    @Override
    public void setOnLeftClick() {//跳转到个人信息界面
        startActivity(new Intent(MainActivity.this, UserActivity.class));
        overridePendingTransition(R.anim.global_in, 0);
    }



    /**
     * 将popup_window显示出来,并添加动画效果
     */

    private void showPopupWindowWithAnimation() {

        Animation animation = AnimationUtils.loadAnimation(this, R.anim.popup_in);
        popupWindow.setAnimation(animation);
//        animation.startNow();
        popupWindow.getAnimation().start();
        popupWindow.setVisibility(View.VISIBLE);

    }



    /**
     * 将popup_window隐藏,并添加动画效果
     */

    private void hidePopupWindowWithAnimation() {

        Animation animation = AnimationUtils.loadAnimation(this, R.anim.popup_out);
        popupWindow.setAnimation(animation);
//        animation.startNow();
        popupWindow.getAnimation().start();
        popupWindow.setVisibility(View.GONE);
    }

    /**
     * 给popup_window绑定显示数据
     */
    private void bindDataToPopupWindow(Marker marker) {

        ResultBean.Goods goods = (ResultBean.Goods) marker.getObject();

        int distance = Math.round(AMapUtils.calculateLineDistance(new LatLng(goods.getLat(), goods.getLng()), currentLatLng));
        ((TextView) popupWindow.findViewById(R.id.tv_popup_charge)).setText(String.valueOf(goods.getGoodsUsePrice()));
        ((TextView) popupWindow.findViewById(R.id.tv_popup_distance)).setText(String.valueOf(distance));

        ImageView iv_thumbnail= (ImageView) popupWindow.findViewById(R.id.iv_thumb_nail);
        Glide.with(this).load(goods.getGoodsPic())
                .thumbnail(0.1f).into(iv_thumbnail);//加载缩略图
//        Glide.with(this).load("").
        ((TextView) popupWindow.findViewById(R.id.tv_SNCode)).setText(String.valueOf(goods.getGoodsSNID()));
        ((TextView) popupWindow.findViewById(R.id.tv_goods_name)).setText(String.valueOf(goods.getGoodsName()));
        ((TextView) popupWindow.findViewById(R.id.tv_business_type)).setText(goods.getBusinessType()==1?"押金预约":"扫码预约");//不可大于4个字,否则双行显示,不合适!
        ((TextView) popupWindow.findViewById(R.id.tv_deposit)).setText(String.valueOf(goods.getGoodsDeposit()));

    }

    @Override
    public void setOnRightClick() {//跳转到搜索界面

        Intent intent = new Intent(this, SearchActivity.class);
        intent.putExtra("currentAddress",currentAddress);
        startActivityForResult(intent, REQUESTCODE);
        overridePendingTransition(R.anim.global_in, 0);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUESTCODE:
                if (resultCode != RESULT_OK)
                    break;
                SearchBean tip = data.getParcelableExtra(SELECTED_ITEM);
                handleSearchRequest(tip);
                break;
            default:
                break;
        }
    }



    /**
     * 在地图上显示用户要搜索的位置
     *
     * @param tip 搜索界面返回的Tip对象,包含LatLng等信息
     */
    private void handleSearchRequest(SearchBean tip) {
        //TODO 处理返回结果
        ToastUtil.toast_debug("获取成功");
        if(tip==null)
            return;
        LatLonPoint point = tip.getLatLonPoint();
        if(point==null){
            ToastUtil.toast("所选地址不存在,请重新搜索");
            return;
        }
        //把地图中心显示到用户选择的poi上
        aMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(new LatLng(point.getLatitude(),point.getLongitude()),16,0,0)));
        //向服务器发送请求,获取在此poi附近的共享物品
        getProductsFromServer(new LatLng(point.getLatitude(),point.getLongitude()));

    }

    @Override
    public void onClick(View v) {

        super.onClick(v);
        switch (v.getId()) {
            case R.id.tv_scanning:
                shouldOpenScanningPage();
                break;
            case R.id.btn_position:
                reposition();
                break;
        }
    }



    /**
     * 开启扫码页面
     */
    private void shouldOpenScanningPage() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.CAMERA)){//展示没有此功能的原因

                new AlertDialog.Builder(this).setCancelable(true).setTitle("相机权限不足,无法启用扫描功能")
                        .setMessage("请到系统设置页面授予该应用相机权限").show();

            }else{//申请权限
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION);
            }
        }else{
            openScanningPage();
        }

    }



    /**
     * 开启扫码页面
     */
    private void openScanningPage() {
        Intent intent = new Intent(this, ScanActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        switch (requestCode){
            case MY_PERMISSION_REQUEST_FINE_LOCATION:
                if(grantResults.length>0
                        &&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    if(mlocationClient!=null){
                        mlocationClient.startLocation();
                    }
                }else{
                    new AlertDialog.Builder(this)
                            .setCancelable(true)
                            .setMessage("定位失败,请到系统设置页面授予此应用定位权限")
                            .show();
                }
                break;
            case REQUEST_CAMERA_PERMISSION:
                if(grantResults.length>0
                        &&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    openScanningPage();
                }else{
                    new AlertDialog.Builder(this).setCancelable(true).setTitle("相机权限不足,无法启动扫描功能")
                            .setMessage("请到系统设置页面授予该应用相机权限").show();
                }
                break;

        }
//        Intent intent = new Intent(this, ScanActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        startActivity(intent);
    }



    /**
     * 首页采用默认的退出动画效果,其他页面采用平移动画效果
     */
    @Override
    public void finish() {

        super.finish();
        overridePendingTransition(0, 0);
    }



    @Override
    public boolean onMarkerClick(Marker marker) {
        //点击某一个marker图标,那么上面要弹出窗口,并显示此marker对应的对象的信息;
        //如果窗口已经显示,就判断是否已经显示当前的marker所对应的对象
        //如果是:不处理
        //如果不是:显示相应对象的信息
//        marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.global_back));

        //判断当前点击的是不是正在显示的Marker呢?

        ResultBean.Goods goods = (ResultBean.Goods) marker.getObject();
        if(selectedGoods==goods)
            return true;
        else{
            selectedGoods=goods;
            marker.remove();
            if (popupWindow.getVisibility() == View.GONE) {
                showPopupWindowWithAnimation();
            }
            bindDataToPopupWindow(marker);

            //请求规划路径
            if (goods != null) {
                Log.d(TAG, "onMarkerClick: lat===" + goods.getLat() + "&lng====" + goods.getLng());
                LatLonPoint endPoint = new LatLonPoint(goods.getLat(), goods.getLng());
                searchRouteResult(endPoint, ROUTE_TYPE_WALK, RouteSearch.WalkDefault);
            } else {
                ToastUtil.toast("当前物品已不存在,无法规划路径");
            }

//            jumpPoint(marker);

            return false;//true表示此接口已经响应事件
        }


    }

    /**
     * marker点击时跳动一下
     */
    public void jumpPoint(final Marker marker) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection proj = aMap.getProjection();
        final LatLng markerLatlng = marker.getPosition();
        Point markerPoint = proj.toScreenLocation(markerLatlng);
        markerPoint.offset(0, -100);
        final LatLng startLatLng = proj.fromScreenLocation(markerPoint);
        final long duration = 1500;

        final Interpolator interpolator = new BounceInterpolator();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed
                        / duration);
                double lng = t * markerLatlng.longitude + (1 - t)
                        * startLatLng.longitude;
                double lat = t * markerLatlng.latitude + (1 - t)
                        * startLatLng.latitude;
                marker.setPosition(new LatLng(lat, lng));
                if (t < 1.0) {
                    handler.postDelayed(this, 16);
                }
            }
        });
    }



    @Override
    public void onWalkRouteSearched(WalkRouteResult result, int errorCode) {
//        if(pd.isShowing()){
//            pd.cancel();
//        }
//        Toast.makeText(MainActivity.this, "执行了onWalkRouteSearched", Toast.LENGTH_SHORT).show();
//        aMap.clear();// 清理地图上的所有覆盖物

        if (walkRouteOverlay != null)
            clearWalkRouteOverlay();

        if (errorCode == AMapException.CODE_AMAP_SUCCESS) {
            if (result != null && result.getPaths() != null) {
                if (result.getPaths().size() > 0) {

                    centerIcon.setVisibility(View.INVISIBLE);//暂时隐藏掉中间的icon

                    mWalkRouteResult = result;
                    final WalkPath walkPath = mWalkRouteResult.getPaths()
                            .get(0);
                    ////////////////////////////////////////
                    //下面这个是绑定路径规划的时间到popup_window中
                    ((TextView) popupWindow.findViewById(R.id.tv_popup_consumed_time)).setText(AMapUtil.getFriendlyTime((int) walkPath.getDuration()));
                    ////////////////////////////////////////
                    //搞了一个图层? 对, 可以考虑封装一个方法专门处理添加步行路径图层@_@
                    walkRouteOverlay = new CustomWalkRouteOverlay(
                            this, aMap, walkPath,
                            mWalkRouteResult.getStartPos(),
                            mWalkRouteResult.getTargetPos());
                    walkRouteOverlay.removeFromMap();//从地图上删除掉原来的旧图层
                    walkRouteOverlay.setNodeIconVisibility(false);
                    walkRouteOverlay.addToMap();
                    addAllProductsMarkers();//重新把所有物品对应的Marker添加到图层上去!
                    walkRouteOverlay.zoomToSpan();//这个方法是不是就是绽放到刚好的那个级别呢*_* 就是这个方法!!!

                } else if (result != null && result.getPaths() == null) {
//                    ToastUtil.show(mContext, R.string.no_result);
                }
            } else {
//                ToastUtil.show(mContext, R.string.no_result);
            }
        } else {
//            ToastUtil.showerror(this.getApplicationContext(), errorCode);
        }
    }



    @Override
    public void onBusRouteSearched(BusRouteResult busRouteResult, int i) {

    }



    @Override
    public void onDriveRouteSearched(DriveRouteResult driveRouteResult, int i) {

    }



    @Override
    public void onRideRouteSearched(RideRouteResult rideRouteResult, int i) {

    }



    @Override
    public void onPopupWindowGone() {

        //直接清除掉所有的图层,然后再重新添加所有的markers
//        clearAllLayer();
//        addAllProductsMarkers();
//        aMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng));
//        aMap.moveCamera(CameraUpdateFactory.zoomTo(16));
        //只移除当前的walkRouteOverlay
        clearWalkRouteOverlay();
        animateToPreviousLevel();
        centerIcon.setVisibility(View.VISIBLE);
        selectedGoods=null;//清空标记

    }



    private void animateToPreviousLevel() {
        aMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(currentLatLng,16f,30,0)));
    }



    private void clearWalkRouteOverlay() {

        if (walkRouteOverlay != null)
            walkRouteOverlay.removeFromMap();
    }



    private void clearAllLayer() {

        if (aMap != null)
            aMap.clear();
    }
}
